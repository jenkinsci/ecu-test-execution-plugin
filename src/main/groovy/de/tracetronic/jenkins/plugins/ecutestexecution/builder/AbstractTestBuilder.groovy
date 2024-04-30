/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.builder


import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.ToolInstallations
import de.tracetronic.jenkins.plugins.ecutestexecution.util.LogConfigUtil
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.StepContext


/**
 * Common base class for all test related steps implemented in this plugin.
 */
abstract class AbstractTestBuilder implements Serializable {
    final String testCasePath
    final TestConfig testConfig
    final ExecutionConfig executionConfig
    final StepContext context

    protected abstract String getTestArtifactName()

    protected abstract LogConfigUtil getLogConfig()

    protected abstract ExecutionOrderBuilder getExecutionOrderBuilder()

    AbstractTestBuilder(String testCasePath, TestConfig testConfig, ExecutionConfig executionConfig,
                        StepContext context) {
        super()
        this.testCasePath = testCasePath
        this.testConfig = testConfig
        this.executionConfig = executionConfig
        this.context = context
    }

    ExecutionConfig getExecutionConfig() {
        return executionConfig ? new ExecutionConfig(executionConfig) : null
    }

    TestConfig getTestConfig() {
        return testConfig ? new TestConfig(testConfig) : null
    }

    /**
     * Calls the execution of the package.
     * @return TestResult
     * results of the test execution
     */
    TestResult runTest() {
        TaskListener listener = context.get(TaskListener.class)
        ToolInstallations toolInstallations = new ToolInstallations(context)
        TestResult result
        try {
            result = context.get(Launcher.class).getChannel().call(new RunTestCallable(testCasePath,
                    context.get(EnvVars.class), listener, executionConfig,
                    getTestArtifactName(), getLogConfig(), getExecutionOrderBuilder(), toolInstallations))
        } catch (Exception e) {
            listener.logger.println("Executing ${testArtifactName} '${testCasePath}' failed!")
            listener.error(e.message)
            context.get(Run.class).setResult(Result.FAILURE)
            result = new TestResult(null, "A problem occurred during the report generation. See caused exception for more details.", null)
        }
        listener.logger.flush()
        return result
    }

    private static final class RunTestCallable extends ControllerToAgentCallableWithTimeout<TestResult, IOException> {

        private static final long serialVersionUID = 1L

        private final String testCasePath
        private final EnvVars envVars
        private final TaskListener listener
        private final ExecutionOrderBuilder executionOrderBuilder
        private final ExecutionConfig executionConfig
        private final String testArtifactName
        private final LogConfigUtil configUtil
        private final ToolInstallations toolInstallations
        private RestApiClient apiClient

        RunTestCallable(final String testCasePath, EnvVars envVars, TaskListener listener,
                        ExecutionConfig executionConfig, String testArtifactName, LogConfigUtil configUtil,
                        ExecutionOrderBuilder executionOrderBuilder, ToolInstallations toolInstallations) {
            super(executionConfig.timeout, listener)
            this.testCasePath = testCasePath
            this.envVars = envVars
            this.listener = listener
            this.executionConfig = executionConfig
            this.testArtifactName = testArtifactName
            this.configUtil = configUtil
            this.executionOrderBuilder = executionOrderBuilder
            this.toolInstallations = toolInstallations

        }

        /**
         * Performs CheckPackageStep if executePackageCheck option was set in the execution config and
         * executes the package
         * @return TestResult results of the test execution
         * @throws IOException
         */
        @Override
        TestResult execute() throws IOException {
            listener.logger.println("Executing ${testArtifactName} '${testCasePath}'...")
            apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))
            if (executionConfig.executePackageCheck) {
                listener.logger.println("Executing package checks for '${testCasePath}'")
                CheckPackageResult check_result = apiClient.runPackageCheck(testCasePath)
                listener.logger.println(check_result.toString())
                if (executionConfig.stopOnError && check_result.result == "ERROR") {
                    listener.logger.println(
                            "Skipping execution of ${testArtifactName} '${testCasePath}' due to failed package checks"
                    )
                    listener.logger.flush()
                    return new TestResult(null, "ERROR", null)
                }
            }
            ExecutionOrder executionOrder = executionOrderBuilder.build()
            configUtil.log()

            ReportInfo reportInfo = apiClient.runTest(executionOrder)

            TestResult result
            if (reportInfo) {
                result = new TestResult(reportInfo.testReportId, reportInfo.result, reportInfo.reportDir)
                listener.logger.println("${StringUtils.capitalize(testArtifactName)} executed successfully.")
            } else {
                result = new TestResult(null, 'ERROR', null)
                listener.logger.println("Executing ${testArtifactName} '${testCasePath}' failed!")
                if (executionConfig.stopOnError) {
                    toolInstallations.stopToolInstances(executionConfig.timeout)
                    if (executionConfig.stopUndefinedTools) {
                        toolInstallations.stopTTInstances(executionConfig.timeout)
                    }
                }
            }
            listener.logger.println(result.toString())
            listener.logger.flush()
            return result
        }

        /**
         * Cancels the package check because it exceeded the configured timeout
         * If the RestApiClientFactory has not return an apiClient for this class yet, it will be canceled there
         */
        @Override
        void cancel() {
            listener.logger.println("Canceling ${testArtifactName} execution!")
            !apiClient ? RestApiClientFactory.setTimeoutExceeded() : apiClient.setTimeoutExceeded()
        }
    }
}
