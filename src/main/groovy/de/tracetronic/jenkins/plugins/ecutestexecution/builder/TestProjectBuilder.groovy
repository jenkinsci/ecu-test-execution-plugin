package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.cxs.generated.et.client.model.AdditionalSettings
import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.LabeledValue
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.util.LogConfigUtil
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.StepContext

/**
 * builder providing test project configuration.
 */
class TestProjectBuilder extends AbstractTestBuilder {
    /**
     * Defines the test artifact name.
     */
    private final static String TEST_ARTIFACT_NAME = 'project'


    TestProjectBuilder(String testCasePath, TestConfig testConfig, ExecutionConfig executionConfig, StepContext context){
        super(testCasePath, testConfig, executionConfig, context)
    }

    @Override
    protected String getTestArtifactName() {
        return TEST_ARTIFACT_NAME
    }

    @Override
    protected ExecutionOrder getExecutionOrder() {
        AdditionalSettings settings = new AdditionalSettings()
                .forceConfigurationReload(testConfig.forceConfigurationReload)
        ExecutionOrder executionOrder = new ExecutionOrder()
                .testCasePath(testCasePath)
                .tcfPath(testConfig.tbcPath)
                .tcfPath(testConfig.tcfPath)
                .constants(testConfig.constants as List<LabeledValue>)
                .additionalSettings(settings)

        return executionOrder
    }

    @Override
    protected LogConfigUtil getLogConfig() {
        return new LogConfigUtil(context.get(TaskListener.class), testConfig)
    }
}
