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
import de.tracetronic.cxs.generated.et.client.model.Recording
import de.tracetronic.cxs.generated.et.client.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunPackageAction
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
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
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import javax.annotation.Nonnull

class RunPackageStep extends RunTestStep {

    @Nonnull
    private PackageConfig packageConfig

    @Nonnull
    private AnalysisConfig analysisConfig

    @DataBoundConstructor
    RunPackageStep(String testCasePath) {
        super(testCasePath)
        this.packageConfig = new PackageConfig([])
        this.analysisConfig = new AnalysisConfig()
    }

    @Nonnull
    PackageConfig getPackageConfig() {
        return packageConfig
    }

    @DataBoundSetter
    void setPackageConfig(PackageConfig packageConfig) {
        this.packageConfig = packageConfig ?: new PackageConfig([])
    }

    @Nonnull
    AnalysisConfig getAnalysisConfig() {
        return analysisConfig
    }

    @DataBoundSetter
    void setAnalysisConfig(AnalysisConfig analysisConfig) {
        this.analysisConfig = analysisConfig ?: new AnalysisConfig()
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<TestResult> {

        private final transient RunPackageStep step

        Execution(RunPackageStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected TestResult run() throws Exception {
            EnvVars envVars = context.get(EnvVars.class)
            String expTestCasePath = envVars.expand(step.testCasePath)
            TestConfig expTestConfig = step.testConfig.expand(envVars)
            PackageConfig expPackageConfig = step.packageConfig.expand(envVars)
            AnalysisConfig expAnalysisConfig = step.analysisConfig.expand(envVars)

            checkPackage(expTestCasePath)

            TestResult result = getContext().get(Launcher.class).getChannel().call(
                    new ExecutionCallable(expTestCasePath, expTestConfig, expPackageConfig,
                            expAnalysisConfig, step.executionConfig,
                            context.get(EnvVars.class), context.get(TaskListener.class)))

            addBuildAction(context.get(Run.class), expTestCasePath, expTestConfig, expPackageConfig, expAnalysisConfig,
                    result)

            return result
        }

        private void checkPackage(String packageFile)
                throws IOException, InterruptedException, IllegalArgumentException {
            if (IOUtils.isAbsolute(packageFile)) {
                FilePath packagePath = new FilePath(context.get(Launcher.class).getChannel(), packageFile)
                if (!packagePath.exists()) {
                    throw new IllegalArgumentException("ECU-TEST package at ${packagePath.getRemote()} does not exist!")
                }
            }
        }

        private void addBuildAction(Run<?, ?> run, String testCasePath, TestConfig testConfig,
                                    PackageConfig packageConfig, AnalysisConfig analysisConfig, TestResult testResult) {
            RunPackageAction action = new RunPackageAction(
                    testCasePath, testConfig, packageConfig, analysisConfig, testResult)
            run.addAction(action)
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<TestResult, IOException> {

        private final String testCasePath
        private final TestConfig testConfig
        private final PackageConfig packageConfig
        private final AnalysisConfig analysisConfig
        private final ExecutionConfig executionConfig
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(String testCasePath, TestConfig testConfig, PackageConfig packageConfig,
                          AnalysisConfig analysisConfig, ExecutionConfig executionConfig,
                          EnvVars envVars, TaskListener listener) {
            super()
            this.testCasePath = testCasePath
            this.testConfig = testConfig.expand(envVars)
            this.packageConfig = packageConfig.expand(envVars)
            this.analysisConfig = analysisConfig.expand(envVars)
            this.executionConfig = executionConfig
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        TestResult call() throws IOException {
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            AdditionalSettings settings = new AdditionalSettings()
                    .forceConfigurationReload(testConfig.forceConfigurationReload)
                    .packageParameters(packageConfig.packageParameters as List<LabeledValue>)
                    .analysisName(analysisConfig.analysisName)
                    .mapping(analysisConfig.mapping)
                    .recordings(analysisConfig.recordings as List<Recording>)
            ExecutionOrder executionOrder = new ExecutionOrder()
                    .testCasePath(testCasePath)
                    .tbcPath(testConfig.tbcPath)
                    .tcfPath(testConfig.tcfPath)
                    .constants(testConfig.constants as List<LabeledValue>)
                    .additionalSettings(settings)

            listener.logger.println("Executing package ${this.testCasePath}...")
            logConfigs()

            de.tracetronic.cxs.generated.et.client.model.Execution execution = apiClient.runTest(
                    executionOrder, executionConfig.timeout)

            TestResult result
            ReportInfo reportInfo = execution.result
            if (reportInfo) {
                result = new TestResult(reportInfo.testReportId, reportInfo.result, reportInfo.reportDir)
                listener.logger.println('Package executed successfully.')
            } else {
                result = new TestResult(null, 'ERROR', null)
                listener.logger.println('Executing Package failed!')
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
            if (packageConfig.packageParameters) {
                listener.logger.println("-> With package parameters=[${this.packageConfig.packageParameters.each { it.toString() }}]")
            }
            if (analysisConfig.analysisName) {
                listener.logger.println("-> With analysis=${this.analysisConfig.analysisName}")
            }
            if (analysisConfig.mapping) {
                listener.logger.println("-> With mapping=${this.analysisConfig.mapping}")
            }
            if (analysisConfig.recordings) {
                listener.logger.println("-> With analysis recordings=[${this.analysisConfig.recordings.each { it.toString() }}]")
            }
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        @Override
        String getFunctionName() {
            'ttRunPackage'
        }

        @Override
        String getDisplayName() {
            '[TT] Run an ECU-TEST package'
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
