package de.tracetronic.jenkins.plugins.ecutestexecution.builder


import de.tracetronic.cxs.generated.et.client.model.Execution
import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.util.LogConfigUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import hudson.EnvVars
import hudson.Launcher
import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.StepContext

/**
 * Common base class for all test related steps implemented in this plugin.
 */
abstract class AbstractTestBuilder {
    static String testCasePath
    static TestConfig testConfig
    static ExecutionConfig executionConfig
    static StepContext context

    protected abstract String getTestArtifactName()
    protected abstract LogConfigUtil getLogConfig()
    protected abstract ExecutionOrder getExecutionOrder()

    AbstractTestBuilder(String testCasePath, TestConfig testConfig, ExecutionConfig executionConfig, StepContext context) {
        super()
        this.testCasePath = testCasePath
        this.testConfig = testConfig
        this.executionConfig = executionConfig
        this.context = context
    }

    TestResult runTest() {
        return context.get(Launcher.class).getChannel().call(new RunTestCallable(testCasePath, context,
                getExecutionOrder(), executionConfig, getTestArtifactName(), getLogConfig()))
    }

    private static final class RunTestCallable extends MasterToSlaveCallable<TestResult, IOException> {
        private final String testCasePath
        //@TODO transient or not transient?
        private final transient StepContext context
        //@TODO transient or not transient?
        private final transient ExecutionOrder executionOrder
        private final ExecutionConfig executionConfig
        private final String testArtifactName
        //@TODO transient or not transient?
        private final transient LogConfigUtil configUtil

        RunTestCallable(final String testCasePath, final StepContext context, ExecutionOrder executionOrder,
                        ExecutionConfig executionConfig, String testArtifactName, LogConfigUtil configUtil) {
            this.testCasePath = testCasePath
            this.context = context
            this.executionOrder = executionOrder
            this.executionConfig = executionConfig
            this.testArtifactName = testArtifactName
            this.configUtil = configUtil
        }

        @Override
        TestResult call() throws IOException {
            EnvVars envVars = context.get(EnvVars.class)
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            TaskListener listener = context.get(TaskListener.class)
            listener.logger.println("Executing ${testArtifactName} ${testCasePath}...")
            configUtil.log()

            Execution execution = apiClient.runTest(
                    executionOrder, executionConfig.timeout)

            TestResult result
            ReportInfo reportInfo = execution.result
            if (reportInfo) {
                result = new TestResult(reportInfo.testReportId, reportInfo.result, reportInfo.reportDir)
                listener.logger.println("${StringUtils.capitalize(testArtifactName)} executed successfully.")
            } else {
                result = new TestResult(null, 'ERROR', null)
                listener.logger.println("Executing ${testArtifactName} failed!")
                if (executionConfig.stopOnError) {
                    stopToolInstances()
                }
            }
            listener.logger.println(result.toString())

            return result
        }

        private stopToolInstances() {
            context.get(TaskListener.class).logger.println('- Stopping running ECU-TEST instances...')
            ProcessUtil.killProcess(ETInstallation.getExeFileName())
            context.get(TaskListener.class).logger.println('-> ECU-TEST stopped successfully.')
        }
    }
}
