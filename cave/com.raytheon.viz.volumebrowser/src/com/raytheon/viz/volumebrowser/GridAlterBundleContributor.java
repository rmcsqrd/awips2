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
package com.raytheon.viz.volumebrowser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.raytheon.uf.common.dataplugin.grid.GridConstants;
import com.raytheon.uf.common.dataquery.requests.RequestConstraint;
import com.raytheon.uf.common.menus.vb.VbSource;
import com.raytheon.uf.common.menus.vb.VbSourceList;
import com.raytheon.uf.viz.core.drawables.AbstractRenderableDisplay;
import com.raytheon.uf.viz.core.drawables.ResourcePair;
import com.raytheon.uf.viz.core.procedures.Bundle;
import com.raytheon.uf.viz.core.rsc.AbstractRequestableResourceData;
import com.raytheon.uf.viz.core.rsc.AbstractResourceData;
import com.raytheon.uf.viz.core.rsc.ResourceList;
import com.raytheon.uf.viz.core.rsc.groups.BlendedResourceData;
import com.raytheon.uf.viz.d2d.core.procedures.AlterBundleContributorAdapter;
import com.raytheon.uf.viz.xy.crosssection.rsc.CrossSectionResourceData;
import com.raytheon.uf.viz.xy.timeseries.rsc.TimeSeriesResourceData;
import com.raytheon.uf.viz.xy.varheight.rsc.VarHeightResourceData;

/**
 * Class to handle alter bundles for grids.
 * 
 * <pre>
 * 
 * SOFTWARE HISTORY
 * Date          Ticket#  Engineer  Description
 * ------------- -------- --------- -----------------------------------------
 * Jan 04, 2010           mschenke  Initial creation
 * Oct 03, 2012  1248     rferrel   Change to use adapter.
 * Dec 11, 2013  2602     bsteffen  Remove dead catch block.
 * Aug 19, 2014  3506     mapeters  Modified getModelTitleToNameMap function 
 *                                  to still get correct VB sources after 
 *                                  splitting them into multiple files.
 * Aug 03, 2015  3861     bsteffen  Remove nsharp dependency.
 * Jun 24, 2019  ----     mjames    Remove DatasetInfo
 * 
 * </pre>
 * 
 * @author mschenke
 * @version 1.0
 */

public class GridAlterBundleContributor extends AlterBundleContributorAdapter {

    private static final String GRID_KEY = "Grid";

    private static Map<String, String> modelTitleToNameMap = null;

    private Map<String, String> getModelTitleToNameMap() {

        if (modelTitleToNameMap == null) {
            modelTitleToNameMap = new HashMap<String, String>();
            for (VbSource source : VbSourceList.getInstance().getAllSources()) {
                if (!source.getRemove()) {
                    modelTitleToNameMap.put(source.getName(), source.getKey());
                }
            }
        }
        return modelTitleToNameMap;
    }

    private void alterResourceList(ResourceList list, String selectedString) {
        for (ResourcePair rp : list) {
            AbstractResourceData rData = rp.getResourceData();
            if (rData instanceof BlendedResourceData) {
                alterResourceList(
                        ((BlendedResourceData) rData).getResourceList(),
                        selectedString);
            } else if (rData instanceof AbstractRequestableResourceData) {
                alterResource((AbstractRequestableResourceData) rData,
                        selectedString);
            }
        }
    }

    private void alterResource(AbstractRequestableResourceData data,
            String selectedString) {
        Map<String, RequestConstraint> reqMap = data.getMetadataMap();
        if (selectedString != null) {
            reqMap.put(GridConstants.DATASET_ID, new RequestConstraint(
                    selectedString));

            // next, need to modify for other displays (not plan view)
            if (data instanceof VarHeightResourceData) {
                ((VarHeightResourceData) data).setSource(selectedString);
            } else if (data instanceof TimeSeriesResourceData) {
                ((TimeSeriesResourceData) data).setSource(selectedString);
            } else if (data instanceof CrossSectionResourceData) {
                ((CrossSectionResourceData) data).setSource(selectedString);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.raytheon.uf.viz.core.procedures.IAlterBundleContributor#getAlterables
     * ()
     */
    @Override
    public Map<String, String[]> getAlterables() {
        Map<String, String[]> alterables = new HashMap<String, String[]>();
        List<String> models = new ArrayList<String>(getModelTitleToNameMap()
                .keySet());
        Collections.sort(models);
        alterables.put(GRID_KEY, models.toArray(new String[models.size()]));
        return alterables;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.raytheon.uf.viz.core.procedures.IAlterBundleContributor#alterBundle
     * (com.raytheon.uf.viz.core.procedures.Bundle, java.lang.String,
     * java.lang.String)
     */
    @Override
    public void alterBundle(Bundle bundleToAlter, String alterKey,
            String alterValue) {
        if (GRID_KEY.equals(alterKey)) {
            for (AbstractRenderableDisplay display : bundleToAlter
                    .getDisplays()) {
                alterResourceList(display.getDescriptor().getResourceList(),
                        getModelTitleToNameMap().get(alterValue));
            }
        }
    }
}
