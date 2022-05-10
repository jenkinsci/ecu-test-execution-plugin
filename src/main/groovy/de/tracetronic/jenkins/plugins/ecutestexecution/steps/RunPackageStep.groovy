/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunPackageAction
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.TestPackageBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.FormValidation
import hudson.util.IOUtils
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import javax.annotation.Nonnull

/**
 * Step providing the execution of ECU-TEST packages.
 */
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

            TestPackageBuilder testPackage = new TestPackageBuilder(expTestCasePath, expTestConfig,
                    step.executionConfig, context, expPackageConfig, expAnalysisConfig)
            TestResult result = testPackage.runTest()

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

    /**
     * DescriptorImpl for {@link RunPackageStep}
     */
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
