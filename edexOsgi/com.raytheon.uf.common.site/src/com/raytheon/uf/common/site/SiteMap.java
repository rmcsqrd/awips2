/**
 * This software was developed and / or modified by Raytheon Company,
 * pursuant to Contract DG133W-05-CQ-1067 with the US Government.
 *
 * U.S. EXPORT CONTROLLED TECHNICAL DATA
 * This software product contains export-restricted data whose
 * export/transfer/disclosure is restricted by U.S. law. Dissemination
 * to non-U.S. persons whether in the United States or abroad requires
 * an export license or other authorization.
 *
 * Contractor Name:        Raytheon Company
 * Contractor Address:     6825 Pine Street, Suite 340
 *                         Mail Stop B8
 *                         Omaha, NE 68106
 *                         402.291.0100
 *
 * See the AWIPS II Master Rights File ("Master Rights File.pdf") for
 * further licensing information.
 **/
package com.raytheon.uf.common.site;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.raytheon.uf.common.localization.ILocalizationFile;
import com.raytheon.uf.common.localization.ILocalizationPathObserver;
import com.raytheon.uf.common.localization.IPathManager;
import com.raytheon.uf.common.localization.LocalizationContext;
import com.raytheon.uf.common.localization.LocalizationContext.LocalizationLevel;
import com.raytheon.uf.common.localization.LocalizationContext.LocalizationType;
import com.raytheon.uf.common.localization.PathManagerFactory;
import com.raytheon.uf.common.localization.exception.LocalizationException;
import com.raytheon.uf.common.site.xml.NwsSitesXML;
import com.raytheon.uf.common.site.xml.SiteIdXML;
import com.raytheon.uf.common.status.IUFStatusHandler;
import com.raytheon.uf.common.status.UFStatus;
import com.raytheon.uf.common.status.UFStatus.Priority;

/**
 * TODO Add Description
 *
 * <pre>
 *
 * SOFTWARE HISTORY
 * Date         Ticket#    Engineer    Description
 * ------------ ---------- ----------- --------------------------
 * Jul 16, 2010            bfarmer     Initial creation
 * Apr 09, 2012 DR14765    mhuang      Map out correct CCCC site ID for backup
 *                                      sites.
 * May 15, 2013  1040      mpduff      Add awips_site_list.xml.
 * Mar 18, 2014 DR 17173   D. Friedmna Re-implement DR 14765.
 * Apr 06  2017 DR 19619   MPorricelli Have all edex servers made
 *                                     aware of ndm textdb file change
 * Jan 26, 2018 6863       dgilling    Allow site-level overrides to
 *                                     national_category_table.template, cleanup
 *                                     localization code.
 *
 * </pre>
 *
 * @author bfarmer
 * @version 1.0
 */

public class SiteMap implements ILocalizationPathObserver {
    private static final IUFStatusHandler statusHandler = UFStatus
            .getHandler(SiteMap.class);

    private static SiteMap instance = new SiteMap();

    private static final String AFOS_LOOKUP_FILENAME = "textdb/afos_lookup_table.dat";

    private static final String NATIONAL_CATEGORY_TABLE_FILENAME = "textdb/national_category_table.template";

    private static final String SITE_OVERRIDE_FILENAME = "site3LetterTo4LetterOverride.dat";

    private static final String RFC_TABLE_FILENAME = "textdb/rfc_lookup_table.dat";

    private static final String LOCATION_ID_FILENAME = "awips_site_list.xml";

    private final List<String> rfcList = new ArrayList<>();

    private final Map<String, String> siteToSiteMap = new HashMap<>();

    private final Map<String, String> nationalCategoryMap = new HashMap<>();

    private final Map<String, String> siteTo4LetterSite = new HashMap<>();

    private final Map<String, Set<String>> siteTo3LetterSite = new HashMap<>();

    private final Set<String> site3to4LetterOverrides = new HashSet<>();

    private final Map<String, SiteData> siteMap = new TreeMap<>();

    /** JAXB context */
    private JAXBContext jax;

    /** Unmarshaller object */
    private Unmarshaller unmarshaller;

    /**
     * Get an instance.
     *
     * @return the instance
     */
    public static SiteMap getInstance() {
        return instance;
    }

    private SiteMap() {
        Class[] classes = new Class[] { NwsSitesXML.class, SiteIdXML.class };

        try {
            jax = JAXBContext.newInstance(classes);
            this.unmarshaller = jax.createUnmarshaller();
        } catch (JAXBException e) {
            statusHandler.error("Error creating context for SiteMap", e);
            throw new ExceptionInInitializerError(
                    "Error creating context for SiteMap");
        }
        readFiles();

        PathManagerFactory.getPathManager().addLocalizationPathObserver(
                NATIONAL_CATEGORY_TABLE_FILENAME, this);
        PathManagerFactory.getPathManager()
                .addLocalizationPathObserver(AFOS_LOOKUP_FILENAME, this);
    }

    public synchronized String getCCCFromXXXCode(String xxx) {
        if (nationalCategoryMap.isEmpty() || siteToSiteMap.isEmpty() ) {
            readFiles();
        }
        String retval = null;
        if (xxx != null) {
            if (xxx.length() == 3) {
                retval = siteToSiteMap.get(getSite4LetterId(xxx));
                if (retval == null) {
                    retval = nationalCategoryMap.get(getSite4LetterId(xxx));
                }
            } else if (xxx.length() == 4) {
                retval = siteToSiteMap.get(xxx);
                if (retval == null) {
                    retval = nationalCategoryMap.get(xxx);
                }
            }
        }

        return retval;
    }

    /**
     * Attempt to map a station id (icao?) to a cccid. Use the afos_lookup_table.dat 
     * data only.
     *
     * @param xxx
     *            An id to map.
     * @return
     */
    public synchronized String getAFOSTableMap(String xxx) {
        if (siteToSiteMap.isEmpty()) {
            readFiles();
        }
        return siteToSiteMap.get(xxx);
    }

    public synchronized String mapICAOToCCC(String icao) {
        if (nationalCategoryMap.isEmpty()) {
            readFiles();
        }
        return nationalCategoryMap.get(icao);
    }

    public synchronized void setDirty() {
        siteToSiteMap.clear();
        nationalCategoryMap.clear();
        siteTo4LetterSite.clear();
        siteTo3LetterSite.clear();
        site3to4LetterOverrides.clear();
        rfcList.clear();
        siteMap.clear();
    }
    public synchronized void readFiles() {
        IPathManager pathMgr = PathManagerFactory.getPathManager();
        LocalizationContext[] searchOrder = pathMgr
                .getLocalSearchHierarchy(LocalizationType.COMMON_STATIC);

        List<LocalizationContext> reverseOrder = Arrays
                .asList(Arrays.copyOf(searchOrder, searchOrder.length));
        Collections.reverse(reverseOrder);
        for (LocalizationContext ctx : reverseOrder) {
            ILocalizationFile file = pathMgr.getLocalizationFile(ctx,
                    AFOS_LOOKUP_FILENAME);
            loadAfosLookupFile(file, siteToSiteMap);
        }

        // load national category
        ILocalizationFile locFile = null;
        for (LocalizationContext ctx : searchOrder) {
            ILocalizationFile file = pathMgr.getLocalizationFile(ctx,
                    NATIONAL_CATEGORY_TABLE_FILENAME);
            if ((file != null) && file.exists()) {
                if (locFile == null) {
                    locFile = file;
                } else {
                    if (file.getContext()
                            .getLocalizationLevel() != LocalizationLevel.BASE) {
                        String msg = String.format(
                                "National Category Table override [%s] potentially conflicts with file [%s].",
                                locFile, file);
                        statusHandler.warn(msg);
                    }
                }
            }
        }
        loadNationalCategoryFile(locFile, nationalCategoryMap);

        LocalizationContext lc = pathMgr.getContext(
                LocalizationType.COMMON_STATIC, LocalizationLevel.BASE);
        File file = pathMgr.getFile(lc, RFC_TABLE_FILENAME);
        loadRFCLookupFile(file, rfcList);

        // Load site list
        lc = pathMgr.getContext(LocalizationType.COMMON_STATIC,
                LocalizationLevel.SITE);
        file = pathMgr.getFile(lc, LOCATION_ID_FILENAME);
        if (file == null || !file.exists()) {
            lc = pathMgr.getContext(LocalizationType.COMMON_STATIC,
                    LocalizationLevel.BASE);
            file = pathMgr.getFile(lc, LOCATION_ID_FILENAME);
            statusHandler.info(
                    "Loaded location ID file [" + LOCATION_ID_FILENAME + "]");
        }
        loadSiteListFile(file);

        // post-process the nationalCategoryMap to generate the 3 to 4 letter
        // mapping
        for (String icao : nationalCategoryMap.keySet()) {
            if (icao.trim().length() == 4) {
                String threeId = icao.substring(1);
                String prefixCode = icao.substring(0, 1);
                synchronized (siteTo4LetterSite) {
                    String foundId = siteTo4LetterSite.get(threeId);
                    // US contiguous prefix code "K" takes precedence
                    if (foundId == null || "k".equals(prefixCode)) {
                        siteTo4LetterSite.put(threeId, icao);
                    }
                }
                synchronized (siteTo3LetterSite) {
                    Set<String> reverse = siteTo3LetterSite.get(icao);
                    if (reverse == null) {
                        reverse = new TreeSet<>();
                        siteTo3LetterSite.put(icao, reverse);
                    }
                    reverse.add(icao.substring(1));
                }
            }
        }

        // load site 3 letter to 4 letter override
        lc = pathMgr.getContext(LocalizationType.COMMON_STATIC,
                LocalizationLevel.BASE);
        file = pathMgr.getFile(lc, SITE_OVERRIDE_FILENAME);
        loadSite3LetterTo4LetterOverrideFile(file, siteTo4LetterSite,
                siteTo3LetterSite);
    }

    private synchronized void loadAfosLookupFile(ILocalizationFile file,
            Map<String, String> aliasMap) {
        if ((file != null) && file.exists()) {
            try (BufferedReader fis = new BufferedReader(
                    new InputStreamReader(file.openInputStream()))) {
                String line = null;

                while ((line = fis.readLine()) != null) {
                    String dataKey = line.substring(0, 4);
                    String tblData = line.substring(5);
                    aliasMap.put(dataKey, tblData);
                }
            } catch (IOException | LocalizationException e) {
                statusHandler.error("Could not read AFOS Lookup File "
                        + file, e);
            }
        }
    }

    private synchronized void loadNationalCategoryFile(ILocalizationFile file,
            Map<String, String> aliasMap) {
        if ((file != null) && file.exists()) {
            try (BufferedReader fis = new BufferedReader(
                    new InputStreamReader(file.openInputStream()))) {
                String line = null;

                while ((line = fis.readLine()) != null) {
                    if (line.length() == 9) {
                        String dataKey = line.substring(0, 4);
                        String tblData = line.substring(6);
                        aliasMap.put(dataKey, tblData);
                    }
                }
            } catch (IOException | LocalizationException e) {
                statusHandler.error(
                        "Could not read National Category Table " + file, e);
            }
        }
    }

    private synchronized void loadSite3LetterTo4LetterOverrideFile(File file,
            Map<String, String> site3To4LetterMap,
            Map<String, Set<String>> site4To3LetterMap) {
        if ((file != null) && file.exists()) {
            try (BufferedReader fis = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file)))) {
                String line = null;
                try {
                    while ((line = fis.readLine()) != null) {
                        line = line.trim();
                        if (line.length() >= 8 && !line.startsWith("#")) {
                            String site3 = line.substring(0, 3);
                            String site4 = line.substring(4);

                            // If this overrides an existing entry,
                            // remove the site3 from the reverse lookup map
                            String old4letter = site3To4LetterMap.get(site3);
                            if (old4letter != null) {
                                Set<String> values3 = site4To3LetterMap
                                        .get(old4letter);
                                values3.remove(site3);
                                if (values3.isEmpty()) {
                                    site4To3LetterMap.remove(old4letter);
                                }
                            }

                            // Currently, only the 3-letter IDs are used (in
                            // getSite4LetterId(), but it should be possible
                            // to also add the 4-letter IDs to this set.
                            site3to4LetterOverrides.add(site3);

                            site3To4LetterMap.put(site3, site4);

                            // Add the entry to the reverse lookup map
                            Set<String> site3s = site4To3LetterMap.get(site4);
                            if (site3s == null) {
                                site3s = new TreeSet<>();
                                site4To3LetterMap.put(site4, site3s);
                            }
                            site3s.add(site3);
                        }
                    }
                } catch (IOException e) {
                    statusHandler.handle(Priority.PROBLEM,
                            "Could not read Site 3 Letter To 4 Letter Override "
                                    + SITE_OVERRIDE_FILENAME, e);

                }
            } catch (FileNotFoundException e) {
                statusHandler.handle(Priority.PROBLEM,
                        "Could not find Site 3 Letter To 4 Letter Override "
                                + SITE_OVERRIDE_FILENAME, e);

            } catch (IOException e) {
                statusHandler
                        .error("Could not close Site 3 Letter To 4 Letter Override "
                                + SITE_OVERRIDE_FILENAME, e);
            }
        }
    }

    private synchronized void loadRFCLookupFile(File file, List<String> aliasList) {
        if ((file != null) && file.exists()) {
            try (BufferedReader fis = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file)))) {
                String line = null;
                try {
                    while ((line = fis.readLine()) != null) {
                        aliasList.add(line.trim());
                    }
                } catch (IOException e) {
                    statusHandler.handle(Priority.PROBLEM,
                            "Could not read RFC Lookup File "
                                    + RFC_TABLE_FILENAME, e);

                }
            } catch (FileNotFoundException e) {
                statusHandler.handle(Priority.PROBLEM,
                        "Failed to find RFC Lookup File " + RFC_TABLE_FILENAME,
                        e);

            } catch (IOException e) {
                statusHandler.error(
                        "Could not close RFC Lookup File " + RFC_TABLE_FILENAME,
                        e);

            }
        }
    }

    private synchronized void loadSiteListFile(File file) {
        if (file != null && file.exists()) {
            NwsSitesXML siteXml;
            try {
                siteXml = (NwsSitesXML) unmarshaller.unmarshal(file);
                for (SiteIdXML xml : siteXml.getSiteIds()) {
                    String id = xml.getId();
                    SiteData sd = new SiteData(id, xml.getType());
                    this.siteMap.put(id, sd);
                }
            } catch (JAXBException e) {
                statusHandler.handle(Priority.PROBLEM,
                        "Problem reading in Site Id File ["
                                + LOCATION_ID_FILENAME + "]", e);
            }
        }
    }

    /**
     * Converts a 3 letter site ID into a 4 letter ID, e.g. OAX to KOAX
     *
     * @param site3LetterId
     *            the 3 letter site id
     * @return
     */
    public String getSite4LetterId(String site3LetterId) {
        if (siteTo4LetterSite.isEmpty()) {
            readFiles();
        }
        String site = null;
        synchronized (siteTo4LetterSite) {
            site = siteTo4LetterSite.get(site3LetterId);
        }
        /* If site not found default to K + 3-letter-ID.
         *
         * Or, if the 4-letter site ID that was looked up does not
         * start with a 'K' and did not come from
         * site3LetterTo4LetterOverride.dat, also return
         * K + 3-letter-ID.
         */
        if (site == null
                || (site.length() > 0 && site.charAt(0) != 'K' &&
                        !site3to4LetterOverrides.contains(site3LetterId))) {
            site = "K" + site3LetterId;
        }

        return site;
    }

    /**
     * Convert a 4 letter site ID into the 3 letter site IDs that convert to it,
     * e.g. KOAX to OAX. Some 3 letter sites convert to the same 4 letter site,
     * so this reverse lookup has to return a collection.
     *
     * @param site4LetterId
     * @return the 3 letter sites that map to the 4 letter site
     */
    public Set<String> getSite3LetterIds(String site4LetterId) {
        if (siteTo3LetterSite.isEmpty())  {
            readFiles();
        }
        Set<String> site3LetterIds = null;
        synchronized (siteTo3LetterSite) {
            site3LetterIds = siteTo3LetterSite.get(site4LetterId);
            if (site3LetterIds == null) {
                site3LetterIds = new TreeSet<>();
                if (site4LetterId == null) {
                    // return empty set
                } else if (site4LetterId.length() <= 3) {
                    site3LetterIds.add(site4LetterId);
                } else {
                    site3LetterIds.add(site4LetterId.substring(1));
                }
            }
        }
        return site3LetterIds;
    }

    public synchronized boolean isRFCSite(String site) {
        if (rfcList.isEmpty()) {
            readFiles();
        }
        return rfcList.contains(site);
    }

    /**
     * Get the site data objects.
     *
     * @return site data objects
     */
    public synchronized Map<String, SiteData> getSiteData() {
        if (siteMap.isEmpty()) {
            readFiles();
        }
        return siteMap;
    }

    @Override
    public void fileChanged(ILocalizationFile file) {
        setDirty();
    }
}
