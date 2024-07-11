/*
* Copyright (c) 2021-2024 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.ProvideReportLogsAction
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil

import hudson.EnvVars
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.ListBoxModel
import jenkins.model.Jenkins
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor

import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ProvideReportLogsStep extends Step {

    @DataBoundConstructor
    ProvideReportLogsStep() {
        super()
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    private static List<String> removeEmptyReportIds(List<String> reportIds) {
        return reportIds.findAll { id -> StringUtils.isNotBlank(id) }
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L

        private final transient ProvideReportLogsStep step

        Execution(ProvideReportLogsStep step, StepContext context) {
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
            def logDirName = "reportLogs"
            Run<?, ?> run = context.get(Run.class)
            FilePath workspace = context.get(FilePath.class)
            Launcher launcher = context.get(Launcher.class)
            EnvVars envVars = context.get(EnvVars.class)
            TaskListener listener = context.get(TaskListener.class)

            if (workspace.child(logDirName).list().size() > 0) {
                listener.logger.println("[WARNING] jenkins workspace report folder includes old files")
            }
            long startTimeMillis = run.getStartTimeInMillis()
            def logDirPath = PathUtil.makeAbsoluteInPipelineHome("${logDirName}", context)

            // Download report logs to workspace
            List<String> logFiles = []
            try {
                logFiles = launcher.getChannel().call(
                        new ExecutionCallable(startTimeMillis, envVars, logDirPath, listener)
                )
            } catch (Exception e) {
                throw e
            }

            if (workspace.child(logDirName).list().size() > 0 && logFiles) {
                FilePath[] reportLogs = workspace.list("${logDirName}/*.log")
                def artifactsMap = new HashMap<String, String>()
                reportLogs.each { log ->
                    def relativePath = log.getRemote().substring(workspace.getRemote().length() + 1)
                    artifactsMap.put(relativePath, relativePath)
                }
                listener.logger.println("Adding report logs to artifacts")
                run.artifactManager.archive(workspace, launcher, listener, artifactsMap)
                run.addAction(new ProvideReportLogsAction(run))

            }
            listener.logger.println("Cleaning report folder in jenkins workspace")
            workspace.child(logDirName).deleteContents()
            listener.logger.flush()
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<List<String>, IOException> {

        private static final long serialVersionUID = 1L

        private List<String> reportIds
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
         * Logs a warning if no reportIds were returned by ecu.test.
         * @return List of strings containing the paths of the logs.
         */
        @Override
        List<String> call() throws IOException {
            listener.logger.println("Providing ecu.test report logs to jenkins.")
            List<String> logs = []
            RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
            List<ReportInfo> reports = apiClient.getAllReports()

            if (reports == null || reports.isEmpty()) {
                listener.logger.println("[WARNING] No report files returned by ecu.test")
            }
            reports*.reportDir.each { String reportDir ->
                if (!checkReportFolderCreationDate(reportDir)) {
                    listener.logger.println("[WARNING] ecu.test report folder includes files older than this run. ${reportDir}")
                }
            }

            reports*.testReportId.each { String reportId ->
                listener.logger.println("Downloading reportFolder for ${reportId}")
                try {
                    File reportFolderZip = apiClient.downloadReportFolder(reportId)
                    File log = extractLogFileFromZip(reportFolderZip, "test/ecu.test_out.log", reportId)
                    logs.add(log.name)
                }
                catch (ApiException e) {
                    if (e instanceof de.tracetronic.cxs.generated.et.client.v2.ApiException && e.code == 404) {
                        throw new de.tracetronic.cxs.generated.et.client.v2.ApiException("[ERROR] Downloading reportFolder is not available for ecu.test < 2024.2!")
                    } else {
                        throw e
                    }
                }
            }
            listener.logger.flush()
            return logs

        }

        boolean checkReportFolderCreationDate(String reportDir) {
            def df = "yyyy-MM-dd_HHmmss"
            def pattern = /\d{4}-\d{2}-\d{2}_\d{6}/
            def matcher = reportDir =~ pattern
            if (matcher) {
                def date = matcher[0]
                Date dateTime1 = new Date().parse(df, date)
                Date dateTime2 = new Date(startTimeMillis)
                return dateTime1 > dateTime2
            } else {
                throw new Exception("No date and time found in the input string.")
            }
        }

        File extractLogFileFromZip(File reportFolderZip, String fileNameToExtract, String newName) {
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(reportFolderZip))
            ZipEntry entry
            while ((entry = zipInputStream.nextEntry) != null) {
                newName
                if (entry.name == fileNameToExtract) {
                    File outputFile = new File("${logDirPath}/${newName}.log")
                    outputFile.parentFile.mkdirs()

                    def outputStream = new FileOutputStream(outputFile)
                    try {
                        outputStream << zipInputStream
                    } finally {
                        outputStream.close()
                    }
                    listener.logger.println("Extracted ${fileNameToExtract} to ${logDirPath}")
                    zipInputStream.close()
                    return outputFile
                }
            }
            throw new Exception("No ecu.test log not found in given report zip!")
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
            'ttProvideReportLogs'
        }

        @Override
        String getDisplayName() {
            '[TT] Provide ecu.test report logs in jenkins.'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
