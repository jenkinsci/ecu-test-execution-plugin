/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.ToolInstallations
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.*
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.springframework.lang.NonNull

import javax.annotation.Nonnull
import java.util.concurrent.TimeoutException

/**
 * Step providing the package checks of ecu.test packages or projects.
 */
class CheckPackageStep extends Step {

    @NonNull
    private final String testCasePath
    @Nonnull
    private ExecutionConfig executionConfig
    /**
     * Instantiates a new [CheckPackageStep].
     * @param testCasePath
     * the file path
     */
    @DataBoundConstructor
    CheckPackageStep(String testCasePath) {
        this.testCasePath = StringUtils.trimToEmpty(testCasePath)
        this.executionConfig = new ExecutionConfig()
    }

    /**
     * @return the file path to the package or project
     */
    @Nonnull
    String getTestCasePath() {
        return testCasePath
    }

    /**
     * @return the execution config
     */
    @Nonnull
    ExecutionConfig getExecutionConfig() {
        return new ExecutionConfig(executionConfig)
    }

    /**
     *
     * @param executionConfig
     * set the execution config
     */
    @DataBoundSetter
    void setExecutionConfig(ExecutionConfig executionConfig) {
        this.executionConfig = executionConfig ?: new ExecutionConfig()
    }

    /**
     * Start the execution of the step with the given context in the workflow
     *
     * @param context
     * @return the execution
     */
    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    /**
     * Execution class of the step
     */

    static class Execution extends SynchronousNonBlockingStepExecution<CheckPackageResult> {

        private static final long serialVersionUID = 1L

        private final transient CheckPackageStep step
        /**
         * Instantiates a new [Execution].
         *
         * @param step
         * the step
         * @param context
         * the context
         */
        Execution(CheckPackageStep step, StepContext context) {
            super(context)
            this.step = step
        }

        /**
         * Call the execution of the step in the build and return the results
         * @return CheckPackageResult
         * the results and findings of the package check
         */
        @Override
        CheckPackageResult run() throws Exception {
            CheckPackageResult result
            TaskListener listener = context.get(TaskListener.class)
            try {
                result = getContext().get(Launcher.class).getChannel().call(
                        new PackageCheckCallable(step.testCasePath, context, step.executionConfig)
                )
            } catch (Exception e) {
                listener.logger.println('Executing Package Checks failed!')
                listener.error(e.message)
                context.get(Run.class).setResult(Result.FAILURE)
                result = new CheckPackageResult(null, null)
            }

            listener.logger.println(result.toString())
            return result
        }
    }

    /**
     * Callable providing the execution of the step in the build
     */
    private  static final class PackageCheckCallable extends ControllerToAgentCallableWithTimeout<CheckPackageResult,Exception> {

        private static final long serialVersionUID = 1L

        private final String testCasePath
        private final StepContext context
        private final EnvVars envVars
        private final TaskListener listener
        private final ExecutionConfig executionConfig
        private final ToolInstallations toolInstallations
        private RestApiClient apiClient
        /**
         * Instantiates a new [ExecutionCallable].
         *
         * @param testCasePath
         * file path to the package / project to be checked
         * @param context
         * the steps context
         * @param executionConfig
         * the steps executionConfig
         * @param toolInstallations
         * ArrayList of strings containing tool installations, which depending on execution cfg will be stopped
         */
        PackageCheckCallable(String testCasePath, StepContext context, ExecutionConfig executionConfig) {
            super(executionConfig.timeout, context.get(TaskListener.class))
            this.testCasePath = testCasePath
            this.context = context
            this.envVars = context.get(EnvVars.class)
            this.listener = context.get(TaskListener.class)
            this.executionConfig = executionConfig
            this.toolInstallations = new ToolInstallations(context)
        }

        /**
         * Calls the package check via the RestApiClient
         * Results and findings of the package/project are printed in the pipeline logs
         * If the package is missing it will also be printed in the pipeline logs
         * Depending on the given executionConfig some or all TT Tool instances are also stopped.
         * @return the results of the package check
         */
        @Override
        CheckPackageResult execute() throws Exception {
            listener.logger.println('Executing Package Checks for: ' + testCasePath + ' ...')
            this.apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
            CheckPackageResult result
            try {
                result = apiClient.runPackageCheck(testCasePath)
            }
            catch (Exception e) {
                if (e instanceof ApiException) {
                    listener.logger.println('Executing Package Checks failed!')
                    listener.logger.println(e.message)
                    result = new CheckPackageResult(null, null)
                }
                else {
                    throw e
                }
            }
            if (result.result == "ERROR" && executionConfig.stopOnError){
                toolInstallations.stopToolInstances(executionConfig.timeout)
                if (executionConfig.stopUndefinedTools) {
                    toolInstallations.stopTTInstances(executionConfig.timeout)
                }
            }
            return result
        }

        @Override
        void cancel() {
            listener.logger.println("Timeout: canceling execution!")
            if (apiClient == null){
                RestApiClientFactory.toggleExecutionTimeout()
            }
            apiClient.toggleExecutionTimeout()
        }
    }

    /**
     * DescriptorImpl for {@link CheckPackageStep}
     */
    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        /**
         * Defines the step name in the pipeline
         */
        @Override
        String getFunctionName() {
            'ttCheckPackage'
        }

        /**
         * Defines the step name displayed in the pipeline editor
         */
        @Override
        String getDisplayName() {
            '[TT] Check an ecu.test package'
        }

        /**
         * Get the required context of the step
         */
        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, Run.class, EnvVars.class, TaskListener.class)
        }
    }
}
