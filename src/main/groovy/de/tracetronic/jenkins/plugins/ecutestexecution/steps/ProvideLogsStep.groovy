/*
* Copyright (c) 2021-2024 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.ProvideFilesBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV1
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.FormValidation
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import java.text.SimpleDateFormat

class ProvideLogsStep extends Step {
    public static final int DEFAULT_TIMEOUT = 36000
    private int timeout

    ProvideLogsStep() {
        super()
        this.timeout = DEFAULT_TIMEOUT
    }

    @DataBoundConstructor
    ProvideLogsStep(int timeout) {
        super()
        this.timeout = timeout
    }

    int getTimeout() {
        return timeout
    }

    @DataBoundSetter
    void setTimeout(int timeout) {
        this.timeout = timeout < 0 ? 0 : timeout
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L

        private final transient ProvideLogsStep step

        Execution(ProvideLogsStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            Run run = context.get(Run.class)
            TaskListener listener = context.get(TaskListener.class)

            long startTimeMillis = run.getStartTimeInMillis()
            String outDirName = "ecu.test-logs"
            String outDirPath = PathUtil.makeAbsoluteInPipelineHome(outDirName, context)

            try {
                ArrayList<String> filePaths = context.get(Launcher.class).getChannel().call(
                        new ExecutionCallable(step.timeout, startTimeMillis, context.get(EnvVars.class), outDirPath, listener)
                )
                def result = new ProvideFilesBuilder(context).archiveFiles(filePaths, outDirName, true)
                if (result) {
                    listener.logger.println("Successfully added ecu.test logs to jenkins.")
                }
            } catch (Exception e) {
                if (e instanceof AbortException) {
                    run.setResult(Result.UNSTABLE)
                } else {
                    run.setResult(Result.FAILURE)
                }
                listener.logger.println('Providing ecu.test logs failed!')
                listener.error(e.message)
            }
            listener.logger.flush()
        }
    }

    private static final class ExecutionCallable extends ControllerToAgentCallableWithTimeout<ArrayList<String>, IOException> {

        private static final long serialVersionUID = 1L

        private final long startTimeMillis
        private final EnvVars envVars
        private final String logDirPath
        private final TaskListener listener
        private RestApiClient apiClient

        private final unsupportedProvideLogsMsg = "Downloading report folders is not supported for this ecu.test version. Please use ecu.test >= 2024.2 instead."

        ExecutionCallable(long timeout, long startTimeMillis, EnvVars envVars, String logDirPath, TaskListener listener) {
            super(timeout, listener)
            this.startTimeMillis = startTimeMillis
            this.envVars = envVars
            this.logDirPath = logDirPath
            this.listener = listener
        }
        /**
         * Calls downloadReportFolder via the RestApiClient for all report ids.
         * Then extracts and saves the ecu.test log out of the returned zip folder.
         * Logs a warning if no reports were returned by ecu.test.
         * @return List of strings containing the paths of the logs.
         */
        @Override
        ArrayList<String> execute() throws IOException {
            listener.logger.println("Providing ecu.test logs to jenkins.")
            ArrayList<String> logFileNames = ["ecu.test_out.log", "ecu.test_err.log"]
            ArrayList<String> logPaths = []
            try {
                RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
                if (apiClient instanceof RestApiClientV1) {
                    throw new AbortException(unsupportedProvideLogsMsg)
                }
                apiClient = (RestApiClientV2) apiClient
                List<ReportInfo> reports = apiClient.getAllReports()

                if (reports == null || reports.isEmpty()) {
                    return []
                }
                for (def report : reports) {
                    String reportDir = report.reportDir.split('/').last()
                    if (!checkCreationDate(reportDir)) {
                        listener.logger.println("[WARNING] ecu.test reports contains folder older than this run. Path: ${report.reportDir}")
                    }
                    File reportFolderZip = apiClient.downloadReportFolder(report.testReportId)
                    List<String> extractedFiles = ZipUtil.extractFilesByExtension(reportFolderZip, logFileNames, "${logDirPath}/${reportDir}")
                    if (extractedFiles.size() != logFileNames.size()) {
                        listener.logger.println("[WARNING] ${report.reportDir} is missing one or all log files!")
                    }
                    logPaths.addAll(extractedFiles)
                }
            }
            catch (ApiException e) {
                if (e instanceof de.tracetronic.cxs.generated.et.client.v2.ApiException && e.code == 404) {
                    throw new AbortException(unsupportedProvideLogsMsg)
                }
            }
            listener.logger.flush()
            return logPaths
        }

        @Override
        void cancel() {
            !apiClient ? RestApiClientFactory.setTimeoutExceeded() : apiClient.setTimeoutExceeded()
        }

        boolean checkCreationDate(String reportDir) {
            String df = "yyyy-MM-dd_HHmmss"
            SimpleDateFormat dateFormat = new SimpleDateFormat(df)
            String pattern = /\d{4}-\d{2}-\d{2}_\d{6}/
            def matcher = reportDir =~ pattern
            if (matcher.find()) {
                String matchedText = matcher.group(0)
                return dateFormat.parse(matchedText) > new Date(startTimeMillis)
            }
            return false
        }

    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        static int getDefaultTimeout() {
            DEFAULT_TIMEOUT
        }

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

        FormValidation doCheckTimeout(@QueryParameter int value) {
            return ValidationUtil.validateTimeout(value)
        }
    }
}
