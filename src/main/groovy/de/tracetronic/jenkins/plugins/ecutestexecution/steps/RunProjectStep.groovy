/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.cxs.generated.et.client.model.AdditionalSettings
import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.LabeledValue
import de.tracetronic.cxs.generated.et.client.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunProjectAction
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.TestProjectBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.FormValidation
import hudson.util.IOUtils
import jenkins.security.MasterToSlaveCallable
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.QueryParameter

/**
 * Step providing the execution of ECU-TEST projects.
 */
class RunProjectStep extends RunTestStep {

    @DataBoundConstructor
    RunProjectStep(String testCasePath) {
        super(testCasePath)
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<TestResult> {

        private final transient RunProjectStep step

        Execution(RunProjectStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected TestResult run() throws Exception {
            EnvVars envVars = context.get(EnvVars.class)
            String expTestCasePath = envVars.expand(step.testCasePath)
            TestConfig expTestConfig = step.testConfig.expand(envVars)

            checkProject(expTestCasePath)

            TestProjectBuilder testProject = new TestProjectBuilder(expTestCasePath, expTestConfig,
                    step.getExecutionConfig(), context)
            TestResult result = testProject.runTest()

            addBuildAction(context.get(Run.class), expTestCasePath, expTestConfig, result)

            return result
        }

        private void checkProject(String projectFile)
                throws IOException, InterruptedException, IllegalArgumentException {
            if (IOUtils.isAbsolute(projectFile)) {
                FilePath projectPath = new FilePath(context.get(Launcher.class).getChannel(), projectFile)
                if (!projectPath.exists()) {
                    throw new IllegalArgumentException("ECU-TEST project at ${projectPath.getRemote()} does not exist!")
                }
            }
        }

        private void addBuildAction(Run<?, ?> run, String testCasePath, TestConfig testConfig, TestResult testResult) {
            RunProjectAction action = new RunProjectAction(testCasePath, testConfig, testResult)
            run.addAction(action)
        }
    }

    /**
     * DescriptorImpl for {@link RunProjectStep}
     */
    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        @Override
        String getFunctionName() {
            'ttRunProject'
        }

        @Override
        String getDisplayName() {
            '[TT] Run an ECU-TEST project'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, Run.class, EnvVars.class, TaskListener.class)
        }

        /**
         * Validates the test case path.
         *
         * @param value the test case path
         * @return the form validation
         */
        FormValidation doCheckTestCasePath(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value, true)
        }
    }
}
