/*
* Copyright (c) 2025 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

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
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull
import java.util.concurrent.TimeoutException

abstract class AbstractDownloadReportStep extends Step implements Serializable {
    protected String outDirName
    protected String supportVersion
    protected PublishConfig publishConfig
    protected List<String> reportIds
    protected hasWarnings

    AbstractDownloadReportStep() {
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
            this.reportIds = StepUtil.trimAndRemoveEmpty(reportIds.split(",").toList())
        } else if (reportIds instanceof List) {
            this.reportIds = StepUtil.trimAndRemoveEmpty(reportIds)
        } else {
            this.reportIds = []
        }
    }

    @DataBoundSetter
    void setPublishConfig(PublishConfig publishConfig) {
        this.publishConfig = publishConfig ?: new PublishConfig()
    }

    protected abstract ArrayList<String> processReport(File reportZip, String reportDirName, String outDirPath, TaskListener listener)

    protected static final class DownloadReportCallable extends ControllerToAgentCallableWithTimeout<ArrayList<String>, IOException> implements Serializable {
        private static final long serialVersionUID = 1L

        private final long startTimeMillis
        private final EnvVars envVars
        private final String outDirPath
        private RestApiClient apiClient
        private final AbstractDownloadReportStep step

        DownloadReportCallable(AbstractDownloadReportStep step, long timeout, StepContext context) {
            super(timeout, context)
            this.startTimeMillis = context.get(Run.class).getStartTimeInMillis()
            this.envVars = context.get(EnvVars.class)
            this.outDirPath = PathUtil.makeAbsoluteInPipelineHome(step.outDirName, context)
            this.step = step
        }

        @Override
        ArrayList<String> execute() throws IOException {
            String unsupportedVersionMsg = "Downloading ${step.outDirName} is not supported for " +
                    "this ecu.test version. Please use ecu.test >= ${step.supportVersion} instead."
            listener.logger.println("Providing ${step.outDirName} to jenkins.")

            try {
                RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'),
                        envVars.get('ET_API_PORT'))
                if (apiClient instanceof RestApiClientV1) {
                    throw new UnsupportedOperationException(unsupportedVersionMsg)
                }

                apiClient = (RestApiClientV2) apiClient
                List<ReportInfo> reports = step.reportIds ? fetchReportsByIds(apiClient) : fetchAllReports(apiClient)

                ArrayList<String> reportPaths = new ArrayList<>()
                for (ReportInfo report : reports) {
                    processSingleReport(apiClient, report, reportPaths)
                }

                listener.logger.flush()
                return reportPaths
            } catch (Exception e) {
                if (e instanceof TimeoutException || e instanceof UnsupportedOperationException) {
                    throw e
                }

                throw new AbortException(e.message)
            }
        }


        private List<ReportInfo> fetchReportsByIds(RestApiClientV2 apiClient) throws AbortException {
            List<ReportInfo> reports = []
            for (String id : step.reportIds) {
                ReportInfo report = apiClient.getReport(id)
                if (report) {
                    reports.add(report)
                } else {
                    handleMissingReport(id)
                }
            }
            return reports
        }

        private List<ReportInfo> fetchAllReports(RestApiClientV2 apiClient) {
            listener.logger.println("Providing all ${step.outDirName}...")
            return apiClient.getAllReports()
        }

        private void processSingleReport(RestApiClientV2 apiClient, ReportInfo report, ArrayList<String> reportPaths)
                throws AbortException {
            String reportDirName = report.reportDir.split('/').last()
            listener.logger.println("Providing ${step.outDirName} for report ${reportDirName}...")
            File reportZip = apiClient.downloadReportFolder(report.testReportId)

            if (reportZip) {
                ArrayList<String> reportPath = step.processReport(reportZip, reportDirName, outDirPath, listener)
                if (reportPath) {
                    reportPaths.addAll(reportPath)
                }
            } else if (step.publishConfig.failOnError) {
                throw new AbortException("Build result set to ${Result.FAILURE.toString()} due to failing download " +
                        "of ${report.testReportId}. Set Pipeline step property 'Fail On Error' to 'false' to ignore " +
                        "any download error.")
            }
        }

        private void handleMissingReport(String id) throws AbortException {
            if (step.publishConfig.failOnError) {
                throw new AbortException("Build result set to ${Result.FAILURE.toString()} due to " +
                        "missing report ${id}. Set Pipeline step property 'Fail On Error' to 'false' to " +
                        "ignore missing reports.")
            } else {
                step.hasWarnings = true
                listener.logger.println("[WARNING] Report with id ${id} could not be found!")
            }
        }

        @Override
        void cancel() {
            !apiClient ? RestApiClientFactory.setTimeoutExceeded() : apiClient.setTimeoutExceeded()
        }
    }
}
