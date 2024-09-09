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
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.stapler.DataBoundConstructor

class ProvideReportsStep extends AbstractProvideStep {
    private static final String ICON_NAME = 'testreport'
    private static final String OUT_DIR_NAME = "ecu.test-reports"
    private static final String SUPPORT_VERSION = "2024.3"

    @DataBoundConstructor
    ProvideReportsStep() {
        super()
        iconName = ICON_NAME
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
    }

    @Override
    protected ArrayList<String> processReport(File reportFolder, String reportDir, String outDirPath, TaskListener listener) {
        ArrayList<String> reportPaths = []

        if (ZipUtil.containsFileOfType(reportFolder, ".prf")) {
            def outputFile = new File("${outDirPath}/${reportDir}/${reportDir}.zip")
            outputFile.parentFile.mkdirs()
            String zipPath = ZipUtil.recreateWithFilesOfType(reportFolder, [".trf", ".prf"], outputFile)
            reportPaths.add(zipPath)
        } else {
            List<String> extractedFiles = ZipUtil.extractFilesByExtension(reportFolder, [".trf", ".prf"], "${outDirPath}/${reportDir}")
            if (extractedFiles.size() == 0) {
                listener.logger.println("[WARNING] Could not find any report files in ${reportDir}!")
            }
            reportPaths.addAll(extractedFiles)
        }
        return reportPaths
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {
        @Override
        String getFunctionName() {
            'ttProvideReports'
        }

        @Override
        String getDisplayName() {
            '[TT] Provide ecu.test reports as job artifacts.'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
