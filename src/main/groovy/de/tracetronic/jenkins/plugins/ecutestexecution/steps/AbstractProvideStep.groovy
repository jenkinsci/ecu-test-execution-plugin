/*
* Copyright (c) 2024 tracetronic GmbH
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
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull

abstract class AbstractProvideStep extends Step {
    protected static String iconName
    protected static String outDirName
    protected static String supportVersion
    protected PublishConfig publishConfig

    AbstractProvideStep() {
        super()
        this.publishConfig = new PublishConfig()
    }

    @Nonnull
    PublishConfig getPublishConfig() {
        return new PublishConfig(publishConfig)
    }

    @DataBoundSetter
    void setPublishConfig(PublishConfig publishConfig) {
        this.publishConfig = publishConfig ?: new PublishConfig()
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    protected abstract ArrayList<String> processReports(RestApiClientV2 apiClient, String outDirPath, TaskListener listener, long startTimeMillis)

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L

        private final transient AbstractProvideStep step

        Execution(AbstractProvideStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            Run run = context.get(Run.class)
            TaskListener listener = context.get(TaskListener.class)

            long startTimeMillis = run.getStartTimeInMillis()
            String outDirPath = PathUtil.makeAbsoluteInPipelineHome(outDirName, context)

            try {
                ArrayList<String> filePaths = context.get(Launcher.class).getChannel().call(
                        new ExecutionCallable(step.publishConfig.timeout, startTimeMillis, context.get(EnvVars.class), outDirPath, listener, step)
                )
                def result = new ProvideFilesBuilder(context).archiveFiles(filePaths, outDirName, step.publishConfig.keepAll, iconName)
                if (!result && !step.publishConfig.allowMissing) {
                    run.setResult(Result.FAILURE)
                    throw new Exception("Missing ${outDirName} aren't allowed by step property. Set build result to ${Result.FAILURE.toString()}")
                }

                result && listener.logger.println("Successfully added ${outDirName} to jenkins.")
            } catch (Exception e) {
                if (e instanceof AbortException) {
                    run.setResult(Result.UNSTABLE)
                } else {
                    run.setResult(Result.FAILURE)
                }
                listener.logger.println("Providing ${outDirName} failed!")
                listener.error(e.message)
            }
            listener.logger.flush()
        }
    }

    private static final class ExecutionCallable extends ControllerToAgentCallableWithTimeout<ArrayList<String>, IOException> {
        private static final long serialVersionUID = 1L

        private final long startTimeMillis
        private final EnvVars envVars
        private final String outDirPath
        private final TaskListener listener
        private RestApiClient apiClient
        private final AbstractProvideStep step

        private final unsupportedVersionMsg = "Downloading ${outDirName} is not supported for this ecu.test version. Please use ecu.test >= ${supportVersion} instead."

        ExecutionCallable(long timeout, long startTimeMillis, EnvVars envVars, String outDirPath, TaskListener listener, AbstractProvideStep step) {
            super(timeout, listener)
            this.startTimeMillis = startTimeMillis
            this.envVars = envVars
            this.outDirPath = outDirPath
            this.listener = listener
            this.step = step
        }

        @Override
        ArrayList<String> execute() throws IOException {
            listener.logger.println("Providing ${outDirName} to jenkins.")
            try {
                RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
                if (apiClient instanceof RestApiClientV1) {
                    throw new AbortException(unsupportedVersionMsg)
                }
                apiClient = (RestApiClientV2) apiClient
                return step.processReports(apiClient, outDirPath, listener, startTimeMillis)
            }
            catch (ApiException e) {
                if (e instanceof de.tracetronic.cxs.generated.et.client.v2.ApiException && e.code == 404) {
                    throw new AbortException(unsupportedVersionMsg)
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
