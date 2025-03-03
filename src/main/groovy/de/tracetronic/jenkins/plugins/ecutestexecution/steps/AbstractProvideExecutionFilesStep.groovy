/*
* Copyright (c) 2024-2025 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.builder.ProvideFilesBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV1
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.StepUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull

abstract class AbstractProvideExecutionFilesStep extends Step implements Serializable {
    protected String iconName
    protected String outDirName
    protected String supportVersion
    protected PublishConfig publishConfig
    protected List<String> reportIds
    protected hasWarnings

    AbstractProvideExecutionFilesStep() {
        super()
        this.publishConfig = new PublishConfig()
        this.reportIds = []
        this.hasWarnings = false
    }

    @Nonnull
    PublishConfig getPublishConfig() {
        return new PublishConfig(publishConfig)
    }

    List<String> getReportIds() {
        return reportIds.collect()
    }

    @DataBoundSetter
    void setReportIds(def reportIds) {
        if (reportIds instanceof String) {
            this.reportIds = reportIds ? reportIds.split(",")*.trim().findAll { it } : []
        } else if (reportIds instanceof List) {
            this.reportIds = StepUtil.removeEmptyReportIds(reportIds)
        } else {
            this.reportIds = []
        }
    }

    @DataBoundSetter
    void setPublishConfig(PublishConfig publishConfig) {
        this.publishConfig = publishConfig ?: new PublishConfig()
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    protected abstract ArrayList<String> processReport(File reportZip, String reportDirName, String outDirPath, TaskListener listener)

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L

        private final transient AbstractProvideExecutionFilesStep step

        Execution(AbstractProvideExecutionFilesStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            Run run = context.get(Run.class)
            TaskListener listener = context.get(TaskListener.class)
            long startTimeMillis = run.getStartTimeInMillis()
            String outDirPath = PathUtil.makeAbsoluteInPipelineHome(step.outDirName, context)

            try {
                ArrayList<String> filePaths = context.get(Launcher.class).getChannel().call(
                        new ExecutionCallable(step.publishConfig.timeout, startTimeMillis, context.get(EnvVars.class), outDirPath, listener, step)
                )
                def result = new ProvideFilesBuilder(context).archiveFiles(filePaths, step.outDirName, step.publishConfig.keepAll, step.iconName)
                if (!result && !step.publishConfig.allowMissing) {
                    throw new Exception("Build result set to ${Result.FAILURE.toString()} due to missing ${step.outDirName}. Adjust AllowMissing step property if this is not intended.")
                }

                result && listener.logger.println("Successfully added ${step.outDirName} to jenkins.")
                if (step.hasWarnings) {
                    run.setResult(Result.UNSTABLE)
                    listener.logger.println("Build result set to ${Result.UNSTABLE.toString()} due to warnings.")
                }
            } catch (Exception e) {
                if (e instanceof UnsupportedOperationException) {
                    run.setResult(Result.UNSTABLE)
                } else {
                    run.setResult(Result.FAILURE)
                }
                listener.logger.println("Providing ${step.outDirName} failed!")
                listener.error(e.message)
            }
            listener.logger.flush()
        }
    }

    private static final class ExecutionCallable extends ControllerToAgentCallableWithTimeout<ArrayList<String>, IOException> implements Serializable {
        private static final long serialVersionUID = 1L

        private final long startTimeMillis
        private final EnvVars envVars
        private final String outDirPath
        private final TaskListener listener
        private RestApiClient apiClient
        private final AbstractProvideExecutionFilesStep step


        ExecutionCallable(long timeout, long startTimeMillis, EnvVars envVars, String outDirPath, TaskListener listener, AbstractProvideExecutionFilesStep step) {
            super(timeout, listener)
            this.startTimeMillis = startTimeMillis
            this.envVars = envVars
            this.outDirPath = outDirPath
            this.listener = listener
            this.step = step
        }

        @Override
        ArrayList<String> execute() throws IOException {
            String unsupportedVersionMsg = "Downloading ${step.outDirName} is not supported for this ecu.test version. Please use ecu.test >= ${step.supportVersion} instead."

            listener.logger.println("Providing ${step.outDirName} to jenkins.")
            try {
                RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
                if (apiClient instanceof RestApiClientV1) {
                    throw new UnsupportedOperationException(unsupportedVersionMsg)
                }

                apiClient = (RestApiClientV2) apiClient
                List<ReportInfo> reports = []
                if (!step.reportIds) {
                    listener.logger.println("Providing all ${step.outDirName}...")
                    reports = apiClient.getAllReports()
                } else {
                    step.reportIds.each { id ->
                        ReportInfo report = apiClient.getReport(id)
                        if (report) {
                            reports.add(report)
                            return
                        }
                        if (step.publishConfig.failOnError) {
                            throw new AbortException("Build result set to ${Result.FAILURE.toString()} due to " +
                                    "missing report ${id}. Set Pipeline step property " +
                                    "'Fail On Error' to 'false' to ignore missing reports.")
                        } else {
                            step.hasWarnings = true
                            listener.logger.println("[WARNING] Report with id ${id} could not be found!")
                        }
                    }
                }

                if (reports == null || reports.isEmpty()) {
                    return []
                }

                ArrayList<String> reportPaths = []
                reports.each { report ->
                    String reportDirName = report.reportDir.split('/').last()
                    listener.logger.println("Providing ${step.outDirName} for report ${reportDirName}...")
                    File reportZip = apiClient.downloadReportFolder(report.testReportId)
                    if (reportZip) {
                        ArrayList<String> reportPath = step.processReport(reportZip, reportDirName, outDirPath, listener)
                        if (reportPath) {
                            reportPaths.addAll(reportPath)
                        }
                        return
                    }
                    if (step.publishConfig.failOnError) {
                        throw new AbortException("Build result set to ${Result.FAILURE.toString()} due to " +
                                "failing download of ${report.testReportId}. Set Pipeline step property " +
                                "'Fail On Error' to 'false' to ignore any download error.")
                    }
                }

                return reportPaths
            }
            catch (ApiException e) {
                if (e instanceof de.tracetronic.cxs.generated.et.client.v2.ApiException && e.code == 404) {
                    throw new UnsupportedOperationException(unsupportedVersionMsg)
                }
            }
            listener.logger.flush()
            return []
        }

        @Override
        void cancel() {
            !apiClient ? RestApiClientFactory.setTimeoutExceeded() : apiClient.setTimeoutExceeded()
        }
    }
}
