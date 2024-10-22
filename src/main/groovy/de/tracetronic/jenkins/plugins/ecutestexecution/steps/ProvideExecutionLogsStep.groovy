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

class ProvideExecutionLogsStep extends AbstractProvideExecutionFilesStep {
    private static final String ICON_NAME = 'logFile'
    private static final String OUT_DIR_NAME = "ecu.test Logs"
    private static final String SUPPORT_VERSION = "2024.2"

    @DataBoundConstructor
    ProvideExecutionLogsStep() {
        super()
        iconName = ICON_NAME
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
    }

    @Override
    ArrayList<String> processReport(final File reportZip, final String reportDirName, final String outDirPath,
                                    final TaskListener listener) {
        ArrayList<String> logFileNames = ["ecu.test_out.log", "ecu.test_err.log"]

        ArrayList<String> extractedFiles = ZipUtil.extractFilesByExtension(reportZip, logFileNames, "${outDirPath}/${reportDirName}")
        if (extractedFiles.size() != logFileNames.size()) {
            listener.logger.println("[WARNING] ${reportDirName} is missing one or all log files!")
        }

        return extractedFiles
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
