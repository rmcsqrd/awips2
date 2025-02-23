##
# This software was developed and / or modified by Raytheon Company,
# pursuant to Contract DG133W-05-CQ-1067 with the US Government.
# 
# U.S. EXPORT CONTROLLED TECHNICAL DATA
# This software product contains export-restricted data whose
# export/transfer/disclosure is restricted by U.S. law. Dissemination
# to non-U.S. persons whether in the United States or abroad requires
# an export license or other authorization.
# 
# Contractor Name:        Raytheon Company
# Contractor Address:     6825 Pine Street, Suite 340
#                         Mail Stop B8
#                         Omaha, NE 68106
#                         402.291.0100
# 
# See the AWIPS II Master Rights File ("Master Rights File.pdf") for
# further licensing information.
##

import AlertVizProcessor
import subprocess

#
# A debug processor that sends messages to standard out
#    
#     SOFTWARE HISTORY
#    
#    Date            Ticket#       Engineer       Description
#    ------------    ----------    -----------    --------------------------
#    05/03/11        9101          cjeanbap       Initial Creation.
#    
# 
#

##
# This is a base file that is not intended to be overridden.
##

class PyShellScriptWrapper(AlertVizProcessor.AlertVizProcessor):

    def process(self, statusMessage, alertMetadata, globalConfiguration):
        scriptName = str(statusMessage.getDetails())
        #print "script name: " + scriptName
        if (not (scriptName.endswith(".tcl"))):
            #print "/bin/sh"
            subprocess.Popen(['/bin/sh', scriptName])
        else:
            #print "/bin/tclsh"
            subprocess.Popen(['/usr/bin/tclsh', scriptName])
