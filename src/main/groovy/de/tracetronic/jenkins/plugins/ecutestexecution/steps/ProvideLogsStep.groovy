/*
* Copyright (c) 2024 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.stapler.DataBoundConstructor

class ProvideLogsStep extends AbstractProvideStep {
    private static final String ICON_NAME = 'logFile'
    private static final String OUT_DIR_NAME = "ecu.test-logs"
    private static final String SUPPORT_VERSION = "2024.2"

    @DataBoundConstructor
    ProvideLogsStep() {
        super()
        iconName = ICON_NAME
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
    }

    @Override
    protected ArrayList<String> processReports(RestApiClientV2 apiClient, String outDirPath, TaskListener listener, long startTime) {
        ArrayList<String> logFileNames = ["ecu.test_out.log", "ecu.test_err.log"]
        ArrayList<String> logPaths = []
        List<ReportInfo> reports = apiClient.getAllReports()

        if (reports == null || reports.isEmpty()) {
            return []
        }

        for (def report : reports) {
            String reportDir = report.reportDir.split('/').last()
            if (!PathUtil.isCreationDateAfter(reportDir, startTime)) {
                listener.logger.println("[WARNING] ${outDirName}  contains folder older than this run. Path: ${report.reportDir}")
            }
            File reportFolderZip = apiClient.downloadReportFolder(report.testReportId)
            List<String> extractedFiles = ZipUtil.extractFilesByExtension(reportFolderZip, logFileNames, "${outDirPath}/${reportDir}")
            if (extractedFiles.size() != logFileNames.size()) {
                listener.logger.println("[WARNING] ${report.reportDir} is missing one or all log files!")
            }
            logPaths.addAll(extractedFiles)
        }

        return logPaths
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {
        @Override
        String getFunctionName() {
            'ttProvideLogs'
        }

        @Override
        String getDisplayName() {
            '[TT] Provide ecu.test logs as job artifacts.'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
