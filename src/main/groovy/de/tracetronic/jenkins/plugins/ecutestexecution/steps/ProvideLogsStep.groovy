/*
* Copyright (c) 2021-2024 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.ProvideLogsAction
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.ListBoxModel
import jenkins.security.MasterToSlaveCallable
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor

import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ProvideLogsStep extends Step {

    @DataBoundConstructor
    ProvideLogsStep() {
        super()
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
        /**
         * Call the execution of the step in the build and archive returned report logs in jenkins.
         * Logs a warning if old files are present in report file folder of the workspace.
         * Clean up report log folder after run.
         * @return Void
         */
        @Override
        protected Void run() throws Exception {
            String logDirName = "reportLogs"
            String logDirPath = PathUtil.makeAbsoluteInPipelineHome(logDirName, context)

            Run<?, ?> run = context.get(Run.class)
            FilePath workspace = context.get(FilePath.class)
            Launcher launcher = context.get(Launcher.class)
            TaskListener listener = context.get(TaskListener.class)

            long startTimeMillis = run.getStartTimeInMillis()

            // Download ecu.test logs to jenkins workspace
            launcher.getChannel().call(
                    new ExecutionCallable(startTimeMillis, context.get(EnvVars.class), logDirPath, listener)
            )

            // Upload ecu.test logs as jenkins artifacts
            if (workspace.child(logDirName).list().size() > 0) {
                FilePath[] reportLogs = workspace.list("${logDirName}/**/*.log")
                def artifactsMap = new HashMap<String, String>()
                reportLogs.each { log ->
                    def relativePath = log.getRemote().substring(workspace.getRemote().length() + 1)
                    artifactsMap.put(relativePath, relativePath)
                }
                run.artifactManager.archive(workspace, launcher, listener, artifactsMap)
                run.addAction(new ProvideLogsAction(run))
                workspace.child(logDirName).deleteContents()
                listener.logger.println("Successfully added ecu.test logs to jenkins.")
            }
            listener.logger.flush()
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<Void, IOException> {

        private static final long serialVersionUID = 1L

        private final long startTimeMillis
        private final EnvVars envVars
        private final String logDirPath
        private final TaskListener listener


        ExecutionCallable(long startTimeMillis, EnvVars envVars, String logDirPath, TaskListener listener) {
            super()
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
        Void call() throws IOException {
            listener.logger.println("Providing ecu.test logs to jenkins.")
            try {
                RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
                List<ReportInfo> reports = apiClient.getAllReports()

                if (reports == null || reports.isEmpty()) {
                    listener.logger.println("[WARNING] No report files returned by ecu.test")
                }
                for (def report : reports) {
                    String reportDir = report.reportDir.split('/').last()
                    if (!checkReportFolderCreationDate(reportDir)) {
                        listener.logger.println("[WARNING] ecu.test reports contains folder older than this run. Path: ${report.reportDir}")
                    }

                    File reportFolderZip = apiClient.downloadReportFolder(report.testReportId)
                    if (!extractAndSaveFromZip(reportFolderZip, ["ecu.test_out", "ecu.test_err"], "${logDirPath}/${reportDir}")) {
                        throw new AbortException("${report.reportDir} is missing one or all log files!")
                    }
                }
            }
            catch (ApiException e) {
                if (e instanceof de.tracetronic.cxs.generated.et.client.v2.ApiException && e.code == 404) {
                    throw new AbortException("[ERROR] Downloading reportFolder is not available for ecu.test < 2024.2!")
                } else {
                    throw new AbortException(e.message)
                }
            }
            listener.logger.flush()
        }


        /**
         * Checks if given reportDir name, which includes the date was created after this build was started
         * @return boolean
         */
        boolean checkReportFolderCreationDate(String reportDir) {
            String df = "yyyy-MM-dd_HHmmss"
            SimpleDateFormat dateFormat = new SimpleDateFormat(df)
            String pattern = /\d{4}-\d{2}-\d{2}_\d{6}/
            def matcher = reportDir =~ pattern
            if (matcher.find()) {
                String matchedText = matcher.group(0)
                return dateFormat.parse(matchedText) > new Date(startTimeMillis)
            }
            listener.logger.println("[WARNING] Could not extract date from report folder name: ${reportDir}")
            return false
        }

        /**
         * Extracts and saves files containing the given strings in their name out of a given zip folder to given path.
         * @return boolean
         */
        static boolean extractAndSaveFromZip(File reportFolderZip, List<String> extractFilesContaining, String saveToPath) {
            int found = 0
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(reportFolderZip))
            ZipEntry entry
            while ((entry = zipInputStream.nextEntry) != null) {
                for (String str : extractFilesContaining) {
                    if (entry.name.contains(str)) {
                        File outputFile = new File("${saveToPath}/${str}.log")
                        outputFile.parentFile.mkdirs()
                        def outputStream = new FileOutputStream(outputFile)
                        try {
                            outputStream << zipInputStream
                        } finally {
                            outputStream.close()
                        }
                        found++
                    }
                }
            }
            zipInputStream.close()
            if (extractFilesContaining.size() == found) {
                return true
            }
            return false
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {
        private static final List<String> REPORT_GENERATORS = Arrays.asList('ATX', 'EXCEL', 'HTML', 'JSON', 'OMR',
                'TestSpec', 'TRF-SPLIT', 'TXT', 'UNIT')

        static ListBoxModel doFillGeneratorNameItems() {
            ListBoxModel model = new ListBoxModel()
            REPORT_GENERATORS.each { name ->
                model.add(name)
            }
            return model
        }

        @Override
        String getFunctionName() {
            'ttProvideLogs'
        }

        @Override
        String getDisplayName() {
            '[TT] Provide ecu.test logs in jenkins.'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
