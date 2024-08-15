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

import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import java.text.SimpleDateFormat

class ProvideReportsStep extends Step {
    public static final int DEFAULT_TIMEOUT = 36000
    private int timeout

    ProvideReportsStep() {
        super()
        this.timeout = DEFAULT_TIMEOUT
    }

    @DataBoundConstructor
    ProvideReportsStep(int timeout) {
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

        private final transient ProvideReportsStep step

        Execution(ProvideReportsStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            Run run = context.get(Run.class)
            TaskListener listener = context.get(TaskListener.class)

            long startTimeMillis = run.getStartTimeInMillis()
            String outDirName = "ecu.test-reports"
            String outDirPath = PathUtil.makeAbsoluteInPipelineHome(outDirName, context)

            try {
                ArrayList<String> filePaths = context.get(Launcher.class).getChannel().call(
                        new ExecutionCallable(step.timeout, startTimeMillis, context.get(EnvVars.class), outDirPath, listener)
                )
                def result = new ProvideFilesBuilder(context).archiveFiles(filePaths, outDirName, true)
                if (result) {
                    listener.logger.println("Successfully added ecu.test reports to jenkins.")
                }
            } catch (Exception e) {
                if (e instanceof AbortException) {
                    run.setResult(Result.UNSTABLE)
                } else {
                    run.setResult(Result.FAILURE)
                }
                listener.logger.println('Providing ecu.test reports failed!')
                listener.error(e.message)
            }
            listener.logger.flush()
        }
    }

    private static final class ExecutionCallable extends ControllerToAgentCallableWithTimeout<ArrayList<String>, IOException> {

        private static final long serialVersionUID = 1L

        private final long startTimeMillis
        private final EnvVars envVars
        private final String reportDirPath
        private final TaskListener listener
        private RestApiClient apiClient

        private final unsupportedVersionMsg = "Downloading report folders is not supported for this ecu.test version. Please use ecu.test >= 2024.2 instead."

        ExecutionCallable(long timeout, long startTimeMillis, EnvVars envVars, String reportDirPath, TaskListener listener) {
            super(timeout, listener)
            this.startTimeMillis = startTimeMillis
            this.envVars = envVars
            this.reportDirPath = reportDirPath
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
            listener.logger.println("Providing ecu.test reports to jenkins.")
            ArrayList<String> reportPaths = []
            try {
                RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
                if (apiClient instanceof RestApiClientV1) {
                    throw new AbortException(unsupportedVersionMsg)
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
                    if (ZipUtil.containsFileOfType(reportFolderZip, ".prf")) {
                        def outputFile = new File("${reportDirPath}/${reportDir}/${reportDir}.zip")
                        outputFile.parentFile.mkdirs()
                        String zipPath = ZipUtil.recreateWithFilesOfType(reportFolderZip, [".trf", ".prf"], outputFile)
                        reportPaths.add(zipPath)

                    } else {
                        List<String> extractedFiles = ZipUtil.extractFilesByExtension(reportFolderZip, [".trf", ".prf"], "${reportDirPath}/${reportDir}")
                        if (extractedFiles.size() == 0) {
                            listener.logger.println("[WARNING] Could not find any report files in ${report.reportDir}!")
                        }
                        reportPaths.addAll(extractedFiles)
                    }
                }
            }
            catch (ApiException e) {
                if (e instanceof de.tracetronic.cxs.generated.et.client.v2.ApiException && e.code == 404) {
                    throw new AbortException(unsupportedVersionMsg)
                }
            }
            listener.logger.flush()
            return reportPaths
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
