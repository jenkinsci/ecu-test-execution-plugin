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
    private final String filePath

    @DataBoundConstructor
    CheckPackageStep(String filePath) {
        this.filePath = StringUtils.trimToEmpty(filePath)
    }

    @Nonnull
    String getFilePath() {
        return filePath
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
                    new ExecutionCallable(envVars,step.filePath, context.get(Launcher.class), context.get(TaskListener.class))
            )

        }


    }
    private  static final class ExecutionCallable extends MasterToSlaveCallable<CheckPackageResult,Exception> {
        private  final EnvVars envVars
        private  final String filePath
        private  final  Launcher launcher
        private final TaskListener listener
        ExecutionCallable(EnvVars envVars,String filePath, Launcher launcher , TaskListener listener){
            this.envVars = envVars
            this.filePath = filePath
            this.launcher = launcher
            this.listener = listener

        }

        @Override
        CheckPackageResult call() throws RuntimeException,TimeoutException, IllegalArgumentException {
            listener.logger.println("Executing Package Checks for: "+ filePath +" ...")
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
            if (!apiClient.waitForAlive()) {
                throw new TimeoutException("Timeout was exceeded for connecting to ECU-TEST!")
            }
            CheckReport packageCheck = apiClient.runPackageCheck(filePath)
            CheckPackageResult result = new CheckPackageResult(packageCheck.getSize(), packageCheck.getIssues())
            listener.logger.println(result.toString())
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
