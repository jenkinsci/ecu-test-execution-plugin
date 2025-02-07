/*
* Copyright (c) 2024-2025 tracetronic GmbH
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
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.zip.ZipFile

class ProvideGeneratedReportsStep extends AbstractProvideExecutionFilesStep {
    private static final String ICON_NAME = 'generateReport'
    private static final String OUT_DIR_NAME = "Generated ecu.test Reports"
    private static final String SUPPORT_VERSION = "2024.3"
    private String selectedReportTypes

    @DataBoundConstructor
    ProvideGeneratedReportsStep() {
        super()
        this.selectedReportTypes = DescriptorImpl.getSelectedReportTypes()
        iconName = ICON_NAME
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
    }

    String getSelectedReportTypes() {
        return this.selectedReportTypes
    }

    @DataBoundSetter
    void setSelectedReportTypes(String selectedReportTypes) {
        this.selectedReportTypes = (selectedReportTypes != null) ? selectedReportTypes : DescriptorImpl.getSelectedReportTypes()
    }

    protected ArrayList<String> processReport(File reportFile, String reportDirName, String outDirPath, TaskListener listener) {
        ArrayList<String> generatedZipPaths = new ArrayList<>()
        ZipFile reportZip = new ZipFile(reportFile)
        Set<String> targetFolderPaths = new HashSet<>()

        String reportTypes = selectedReportTypes.replace("*", "[^/\\\\]*")
        List<String> reportTypesList = reportTypes.split(",\\s*")
        reportZip.entries().each { entry ->
            Path entryPath = Paths.get(entry.name)
            String path = entryPath.getParent().toString()
            for (String reportTypeStr : reportTypesList) {
                String pattern = "regex:(.+(/|\\\\))?${reportTypeStr}"
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern)
                if (entryPath.getParent() && matcher.matches(entryPath.getParent())) {
                    targetFolderPaths.add(path)
                }
            }
        }

        for (String path : targetFolderPaths) {
            def outputFile = new File("${outDirPath}/${reportDirName}/${path}.zip")
            outputFile.parentFile.mkdirs()
            def zipPath = ZipUtil.recreateWithPath(reportFile, path, outputFile,true)
            generatedZipPaths.add(zipPath)
        }


        if (generatedZipPaths.isEmpty()) {
            listener.logger.println("Could not find any matching generated report files in ${reportDirName}!")
        }

        return generatedZipPaths
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        static String getSelectedReportTypes() {
            return GenerateReportsStep.DescriptorImpl.REPORT_GENERATORS.collect { it + "*" }.join(", ")
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
