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

            TestResult result = getContext().get(Launcher.class).getChannel().call(
                    new ExecutionCallable(expTestCasePath, expTestConfig, step.executionConfig,
                            context.get(EnvVars.class), context.get(TaskListener.class)))

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

    private static final class ExecutionCallable extends MasterToSlaveCallable<TestResult, IOException> {

        private final String testCasePath
        private final TestConfig testConfig
        private final ExecutionConfig executionConfig
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(String testCasePath, TestConfig testConfig, ExecutionConfig executionConfig,
                          EnvVars envVars, TaskListener listener) {
            super()
            this.testCasePath = testCasePath
            this.testConfig = testConfig
            this.executionConfig = executionConfig
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        TestResult call() throws IOException {
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            AdditionalSettings settings = new AdditionalSettings()
                    .forceConfigurationReload(testConfig.forceConfigurationReload)
            ExecutionOrder executionOrder = new ExecutionOrder()
                    .testCasePath(testCasePath)
                    .tbcPath(testConfig.tbcPath)
                    .tcfPath(testConfig.tcfPath)
                    .constants(testConfig.constants as List<LabeledValue>)
                    .additionalSettings(settings)

            listener.logger.println("Executing project ${this.testCasePath}...")
            logConfigs()

            de.tracetronic.cxs.generated.et.client.model.Execution execution = apiClient.runTest(
                    executionOrder, executionConfig.timeout)

            TestResult result
            ReportInfo reportInfo = execution.result
            if (reportInfo) {
                result = new TestResult(reportInfo.testReportId, reportInfo.result, reportInfo.reportDir)
                listener.logger.println('Project executed successfully.')
            } else {
                result = new TestResult(null, 'ERROR', null)
                listener.logger.println('Executing Project failed!')
                if (executionConfig.stopOnError) {
                    stopToolInstances()
                }
            }

            listener.logger.println(result.toString())

            return result
        }

        private stopToolInstances() {
            listener.logger.println('- Stopping running ECU-TEST instances...')
            ProcessUtil.killProcess(ETInstallation.getExeFileName())
            listener.logger.println('-> ECU-TEST stopped successfully.')
        }

        private void logConfigs() {
            if (testConfig.tbcPath) {
                listener.logger.println("-> With TBC=${this.testConfig.tbcPath}")
            }
            if (testConfig.tcfPath) {
                listener.logger.println("-> With TCF=${this.testConfig.tcfPath}")
            }
            if (testConfig.constants) {
                listener.logger.println("-> With global constants=[${this.testConfig.constants.each { it.toString() }}]")
            }
        }
    }

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

        FormValidation doCheckTestCasePath(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value, true)
        }
    }
}
