package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.cxs.generated.et.client.model.Execution
import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.ToolInstallations
import de.tracetronic.jenkins.plugins.ecutestexecution.steps.CheckPackageStep
import de.tracetronic.jenkins.plugins.ecutestexecution.util.LogConfigUtil
import hudson.EnvVars
import hudson.Launcher
import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
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
     * Performs CheckPackageStep if executePackageCheck option was set in the execution config and calls the execution
     * of the package.
     * @return TestResult
     * results of the test execution
     */
    TestResult runTest() {
        TaskListener listener = context.get(TaskListener.class)
        ToolInstallations toolInstallations = new ToolInstallations(context)

        if (executionConfig.executePackageCheck){
            CheckPackageStep step = new CheckPackageStep(testCasePath)
            step.setExecutionConfig(executionConfig)
            CheckPackageResult check_result = step.start(context).run()
            if (executionConfig.stopOnError && check_result.result == "ERROR") {
                listener.logger.println(
                        "Skipping execution of ${testArtifactName} ${testCasePath} due to failed package checks"
                )
                return new TestResult(null, "ERROR",null)
            }
        }

        return context.get(Launcher.class).getChannel().call(new RunTestCallable(testCasePath,
                context.get(EnvVars.class), listener, executionConfig,
                getTestArtifactName(), getLogConfig(), getExecutionOrderBuilder(), toolInstallations))
    }

    private static final class RunTestCallable extends MasterToSlaveCallable<TestResult, IOException> {
 
        private static final long serialVersionUID = 1L 

        private final String testCasePath
        private final EnvVars envVars
        private final TaskListener listener
        private final ExecutionOrderBuilder executionOrderBuilder
        private final ExecutionConfig executionConfig
        private final String testArtifactName
        private final LogConfigUtil configUtil
        private final ToolInstallations toolInstallations

        RunTestCallable(final String testCasePath, EnvVars envVars, TaskListener listener,
                        ExecutionConfig executionConfig, String testArtifactName, LogConfigUtil configUtil,
                        ExecutionOrderBuilder executionOrderBuilder, ToolInstallations toolInstallations) {
            this.testCasePath = testCasePath
            this.envVars = envVars
            this.listener = listener
            this.executionConfig = executionConfig
            this.testArtifactName = testArtifactName
            this.configUtil = configUtil
            this.executionOrderBuilder = executionOrderBuilder
            this.toolInstallations = toolInstallations
        }

        @Override
        TestResult call() throws IOException {
            ExecutionOrder executionOrder = executionOrderBuilder.build()
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            listener.logger.println("Executing ${testArtifactName} ${testCasePath}...")
            configUtil.log()

            Execution execution = apiClient.runTest(
                    executionOrder as ExecutionOrder, executionConfig.timeout)

            TestResult result
            ReportInfo reportInfo = execution.result
            if (reportInfo) {
                result = new TestResult(reportInfo.testReportId, reportInfo.result, reportInfo.reportDir)
                listener.logger.println("${StringUtils.capitalize(testArtifactName)} executed successfully.")
            } else {
                result = new TestResult(null, 'ERROR', null)
                listener.logger.println("Executing ${testArtifactName} failed!")
                if (executionConfig.stopOnError) {
                    toolInstallations.stopToolInstances(executionConfig.timeout)
                    if (executionConfig.stopUndefinedTools) {
                        toolInstallations.stopTTInstances(executionConfig.timeout)
                    }
                }
            }
            listener.logger.println(result.toString())
            return result
        }
    }
}
