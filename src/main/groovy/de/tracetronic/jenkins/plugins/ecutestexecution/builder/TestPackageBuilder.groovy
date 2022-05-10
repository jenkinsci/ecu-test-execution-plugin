package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.cxs.generated.et.client.model.AdditionalSettings
import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.LabeledValue
import de.tracetronic.cxs.generated.et.client.model.Recording
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.util.LogConfigUtil
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.StepContext

/**
 * builder providing test package configuration.
 */
class TestPackageBuilder extends AbstractTestBuilder {
    /**
     * Defines the test artifact name.
     */
    private final static String TEST_ARTIFACT_NAME = 'package'
    private static PackageConfig packageConfig
    private static AnalysisConfig analysisConfig

    TestPackageBuilder(String testCasePath, TestConfig testConfig, ExecutionConfig executionConfig,
                       StepContext context, PackageConfig packageConfig, AnalysisConfig analysisConfig){
        super(testCasePath, testConfig, executionConfig, context)
        this.packageConfig = packageConfig
        this.analysisConfig = analysisConfig
    }

    @Override
    protected String getTestArtifactName() {
        return TEST_ARTIFACT_NAME
    }

    @Override
    protected ExecutionOrder getExecutionOrder() {
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

        return executionOrder
    }

    @Override
    protected LogConfigUtil getLogConfig() {
        return new LogConfigUtil(context.get(TaskListener.class),testConfig, packageConfig, analysisConfig)
    }
}
