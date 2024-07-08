/*
* Copyright (c) 2021-2024 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.ProvideReportLogsAction
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil

import hudson.EnvVars
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Executor
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.ListBoxModel
import jenkins.model.StandardArtifactManager
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

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

    static class Execution extends SynchronousNonBlockingStepExecution<GenerationResult> {

        private static final long serialVersionUID = 1L

        private final transient ProvideReportLogsStep step

        Execution(ProvideReportLogsStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected GenerationResult run() throws Exception {
            def logDirName = "reportLogs"
            Run<?, ?> run = context.get(Run.class)
            Launcher launcher = context.get(Launcher.class)
            EnvVars envVars = context.get(EnvVars.class)
            TaskListener listener = context.get(TaskListener.class)

            long startTimeMillis = run.getTimeInMillis()
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

            // Archive logs and add to view
            if (logFiles) {
                FilePath workspace = context.get(FilePath.class)
                FilePath[] reportLogs = workspace.list("${logDirName}/*.log")
                def artifactManager = new StandardArtifactManager(run)

                def artifactsMap = new HashMap<String, String>()
                reportLogs.each { log ->
                    def relativePath = log.getRemote().substring(workspace.getRemote().length() + 1)
                    artifactsMap.put(relativePath, relativePath)
                }

                artifactManager.archive(workspace, launcher, listener, artifactsMap)
                run.addAction(new ProvideReportLogsAction(run))
                listener.logger.flush()
                return null
            }
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

        @Override
        List<String> call() throws IOException {
            List<String> logs = []
            RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
            listener.logger.println("ProvideReportLogsStep ...")
            reportIds = apiClient.getAllReportIds()

            if (reportIds == null || reportIds.isEmpty()) {
                listener.logger.println("[WARNING] No report log files found")
            }
            reportIds.each { reportId ->
                listener.logger.println("Downloading reportFolder for ${reportId}")
                File reportFolderZip = apiClient.downloadReportFolder(reportId)


                def fileNameToExtract = "test/ecu.test_out.log"
                ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(reportFolderZip))
                ZipEntry entry

                while ((entry = zipInputStream.nextEntry) != null) {
                    //TODO check old files  with startTimeMillis
                    //Problem:entry.getCreationTime is null
                    //listener.logger.println("[WARNING] Old files")
                    if (entry.name == fileNameToExtract) {
                        def outputFile = new File("${logDirPath}/${reportId}.log")
                        outputFile.parentFile.mkdirs()

                        def outputStream = new FileOutputStream(outputFile)
                        try {
                            outputStream << zipInputStream
                        } finally {
                            outputStream.close()
                        }

                        listener.logger.println("Extracted ${fileNameToExtract} to ${logDirPath}")
                        logs.add(outputFile.name)
                    }
                }
                zipInputStream.close()
            }
            listener.logger.flush()
            return logs

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
            '[TT] Provide ecu.test report logs'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
