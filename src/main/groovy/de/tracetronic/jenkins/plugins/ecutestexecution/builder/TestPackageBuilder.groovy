package de.tracetronic.jenkins.plugins.ecutestexecution.builder

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
    protected LogConfigUtil getLogConfig() {
        return new LogConfigUtil(context.get(TaskListener.class),testConfig, packageConfig, analysisConfig)
    }

    /**
     * This method provides an ExecutionOrderBuilder, such that the ExecutionOrder pertaining to the configurations in
     * this class can be built on demand.
     * @return ExecutionOrderBuilder
     */
    @Override
    protected ExecutionOrderBuilder getExecutionOrderBuilder() {
        return new ExecutionOrderBuilder(testCasePath, testConfig, packageConfig, analysisConfig)
    }
}
