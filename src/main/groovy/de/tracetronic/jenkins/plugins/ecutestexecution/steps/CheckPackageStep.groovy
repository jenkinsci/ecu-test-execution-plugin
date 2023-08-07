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
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.IOUtils
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
 * Step providing the checking of ECU-TEST packages.
 */
class CheckPackageStep extends Step {
    @NonNull
    private final String testCasePath
    @DataBoundConstructor
    CheckPackageStep(String testCasePath) {
        this.testCasePath = StringUtils.trimToEmpty(testCasePath)
    }

    @Nonnull
    String getTestCasePath() {
        return testCasePath
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<CheckPackageResult> {

        private final transient CheckPackageStep step
        private final timeout = 60

        Execution(CheckPackageStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected CheckPackageResult run() throws Exception {
            EnvVars envVars = context.get(EnvVars.class)
            return getContext().get(Launcher.class).getChannel().call(
                    new ExecutionCallable(envVars,step.testCasePath, context.get(TaskListener.class))
            )

        }


    }
    private  static final class ExecutionCallable extends MasterToSlaveCallable<CheckPackageResult,Exception> {
        private  final EnvVars envVars
        private  final String testCasePath
        private final TaskListener listener
        ExecutionCallable(EnvVars envVars,String testCasePath, TaskListener listener){
            this.envVars = envVars
            this.testCasePath = testCasePath
            this.listener = listener

        }

        @Override
        CheckPackageResult call() throws RuntimeException,TimeoutException, IllegalArgumentException {
            listener.logger.println("Executing checks for: "+ testCasePath)
            if (IOUtils.isAbsolute(testCasePath)) {
                FilePath packagePath = new FilePath(context.get(Launcher.class).getChannel(), packageFile)
                if (!packagePath.exists()) {
                    throw new IllegalArgumentException("ECU-TEST package at ${packagePath.getRemote()} does not exist!")
                }
            }
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
            if (!apiClient.waitForAlive()) {
                throw new TimeoutException("Timeout of ${timeout} seconds exceeded for connecting to ECU-TEST!")
            }
            CheckReport packageCheck = apiClient.runPackageCheck(this.testCasePath)
            CheckPackageResult result = new CheckPackageResult( packageCheck.getSize(), packageCheck.getIssues())
            if (result.getSize() != 0){
                throw new RuntimeException(result.toString())
            }
            else{
                listener.logger.println("Package Checks Success")
            }
            return result
        }
    }

    /**
     * DescriptorImpl for {@link CheckPackageStep}
     */
    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        @Override
        String getFunctionName() {
            'ttCheckPackage'
        }

        @Override
        String getDisplayName() {
            '[TT] Check an ECU-TEST package'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, Run.class, EnvVars.class, TaskListener.class)
        }

    }
}
