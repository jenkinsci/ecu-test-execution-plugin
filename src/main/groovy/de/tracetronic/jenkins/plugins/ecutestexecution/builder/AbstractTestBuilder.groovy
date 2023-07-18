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
import hudson.model.Computer
import hudson.model.Node
import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.StepContext

import java.util.concurrent.TimeoutException

/**
 * Common base class for all test related steps implemented in this plugin.
 */
abstract class AbstractTestBuilder implements Serializable {
    static String testCasePath
    static TestConfig testConfig
    static ExecutionConfig executionConfig
    static StepContext context

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

    TestResult runTest() {

        def toolInstallations = getToolInstallationsOnNode()

        return context.get(Launcher.class).getChannel().call(new RunTestCallable(testCasePath,
                context.get(EnvVars.class), context.get(TaskListener.class), executionConfig,
                getTestArtifactName(), getLogConfig(), getExecutionOrderBuilder(), toolInstallations))
    }

    private static ArrayList<String> getToolInstallationsOnNode() {
        /**
         * This method gets the executable names of the tool installations on the node given by the context. Context is
         * not reasonably available in the MasterToSlaveCallable, so all info which needs a context must be fetched
         * outside.
         *
         * @return list of the executable names of the ECU-TEST installations on the respective node (can also be
         * TRACE-CHECK executables)
         */
        Computer computer = context.get(Launcher).getComputer()
        Node node = computer?.getNode()
        EnvVars envVars = context.get(EnvVars)
        TaskListener listener = context.get(TaskListener)
        if(node) {
            return ETInstallation.getAllExecutableNames(envVars, node, listener)
        } else {
            return null
        }
    }

    private static final class RunTestCallable extends MasterToSlaveCallable<TestResult, IOException> {
        private final String testCasePath
        private final EnvVars envVars
        private final TaskListener listener
        private final ExecutionOrderBuilder executionOrderBuilder
        private final ExecutionConfig executionConfig
        private final String testArtifactName
        private final LogConfigUtil configUtil
        private final ArrayList<String> toolInstallations

        RunTestCallable(final String testCasePath, EnvVars envVars, TaskListener listener,
                        ExecutionConfig executionConfig, String testArtifactName, LogConfigUtil configUtil,
                        ExecutionOrderBuilder executionOrderBuilder, ArrayList<String> toolInstallations) {
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
                    stopToolInstances()
                    if (executionConfig.stopUndefinedTools) {
                        stopTTInstances()
                    }
                }
            }
            listener.logger.println(result.toString())

            return result
        }

        private stopToolInstances() {
            int timeout = executionConfig.timeout
            if (toolInstallations) {
                if (ProcessUtil.killProcesses(toolInstallations, timeout)) {
                    listener.logger.println('-> Tools stopped successfully.')
                }
                else {
                    throw new TimeoutException("Timeout of ${timeout} seconds exceeded for stopping tool!")
                }
            } else {
                listener.logger.println("No Tool Installations to stop were found. No processes killed.")
            }
        }

        private stopTTInstances() {
            int timeout = executionConfig.timeout
            listener.logger.println("Stop TraceTronic tool instances.")
            if (ProcessUtil.killTTProcesses(timeout)) {
                listener.logger.println("Stopped TraceTronic tools successfully.")
            } else {
                throw new TimeoutException("Timeout of ${timeout} seconds exceeded for stopping TraceTronic tools!")
            }
        }
    }
}
