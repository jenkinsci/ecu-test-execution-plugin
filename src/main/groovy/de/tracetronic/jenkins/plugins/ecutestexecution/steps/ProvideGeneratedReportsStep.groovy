/*
* Copyright (c) 2024 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.TaskListener
import hudson.util.ListBoxModel
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

class ProvideGeneratedReportsStep extends AbstractProvideExecutionFilesStep {
    private static final String ICON_NAME = 'generateReport'
    private static final String OUT_DIR_NAME = "generated-ecu.test-reports"
    private static final String SUPPORT_VERSION = "2024.3"
    private String selectedReportTypes

    @DataBoundConstructor
    ProvideGeneratedReportsStep() {
        super()
        this.selectedReportTypes = GenerateReportsStep.DescriptorImpl.REPORT_GENERATORS.collect().join(", ")
        iconName = ICON_NAME
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
    }

    String getSelectedReportTypes() {
        return this.selectedReportTypes
    }

    @DataBoundSetter
    void setSelectedReportTypes(String selectedReportTypes) {
        this.selectedReportTypes = (selectedReportTypes != null) ? selectedReportTypes : GenerateReportsStep.DescriptorImpl.REPORT_GENERATORS.join(", ")
    }


    protected ArrayList<String> processReport(File reportZip, String reportDirName, String outDirPath, TaskListener listener) {
        ArrayList<String> reportPaths = []
        selectedReportTypes.split(",\\s*").each { reportType ->
            def folderEntryPaths = ZipUtil.getAllMatchingPaths(reportZip, "${reportType}*/**")
            if (folderEntryPaths) {
                def folderName = folderEntryPaths[0].substring(0, folderEntryPaths[0].indexOf('/'))
                def outputFile = new File("${outDirPath}/${reportDirName}/${folderName}.zip")
                outputFile.parentFile.mkdirs()
                reportPaths.add(ZipUtil.recreateWithFilesOfType(reportZip, folderEntryPaths, outputFile))
            }
        }
        if (reportPaths.size() == 0) {
            listener.logger.println("[WARNING] Could not find any matching generated report files in ${reportDirName}!")
        }

        return reportPaths
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        static String getSelectedReportTypes() {
            return GenerateReportsStep.DescriptorImpl.REPORT_GENERATORS.join(", ")
        }

        @Override
        String getFunctionName() {
            'ttProvideGeneratedReports'
        }

        @Override
        String getDisplayName() {
            '[TT] Provide generated ecu.test reports as job artifacts.'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
