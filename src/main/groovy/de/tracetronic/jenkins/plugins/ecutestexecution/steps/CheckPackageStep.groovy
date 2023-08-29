/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.cxs.generated.et.client.model.CheckFinding
import de.tracetronic.cxs.generated.et.client.model.CheckReport
import de.tracetronic.jenkins.plugins.ecutestexecution.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.springframework.lang.NonNull

import java.util.concurrent.TimeoutException
import javax.annotation.Nonnull

/**
 * Step providing the package checks of ECU-TEST packages or projects.
 */
class CheckPackageStep extends Step {

    @NonNull
    private final String filePath

    /**
     * Instantiates a new [CheckPackageStep].
     *
     * @param filePath
     * the file path
     */
    @DataBoundConstructor
    CheckPackageStep(String filePath) {
        this.filePath = StringUtils.trimToEmpty(filePath)
    }

    /**
     * @return the file path to the package or project
     */
    @Nonnull
    String getFilePath() {
        return filePath
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
         * @return the results of the package check
         */
        @Override
        protected CheckPackageResult run() throws Exception {
            EnvVars envVars = context.get(EnvVars.class)
            CheckPackageResult result = getContext().get(Launcher.class).getChannel().call(
                    new ExecutionCallable(envVars, step.filePath, context.get(TaskListener.class))
            )
            if (result.issues.size() > 0) {
                throw new Exception('\n' + result)
            }
            return  result
        }
    }

    /**
     * Callable providing the execution of the step in the build
     */
    private  static final class ExecutionCallable extends MasterToSlaveCallable<CheckPackageResult,Exception> {

        private  final EnvVars envVars
        private  final String filePath
        private final TaskListener listener

        /**
         * Instantiates a new [ExecutionCallable].
         *
         * @param envVars
         * the environment variables
         * @param filePath
         * the file path
         * @param listener
         * the listener
         */
        ExecutionCallable(EnvVars envVars, String filePath, TaskListener listener) {
            this.envVars = envVars
            this.filePath = filePath
            this.listener = listener
        }

        /**
         * First waits/ checks if the ECU-TEST Api is alive and then calls the package check via the RestApiClient.
         * @return the results of the package check
         */
        @Override
        CheckPackageResult call() throws RuntimeException,TimeoutException, IllegalArgumentException {
            listener.logger.println('Executing Package Checks for: ' + filePath + ' ...')
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
            if (!apiClient.waitForAlive()) {
                throw new TimeoutException('Timeout was exceeded for connecting to ECU-TEST!')
            }
            CheckReport packageCheck = apiClient.runPackageCheck(filePath)
            def issues = []
            for (CheckFinding issue : packageCheck.issues) {
                def issueMap = [filename: issue.fileName, message: issue.message]
                issues.add(issueMap)
            }
            CheckPackageResult result = new CheckPackageResult(filePath, issues)
            listener.logger.println(result)
            return result
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
            '[TT] Check an ECU-TEST package'
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
