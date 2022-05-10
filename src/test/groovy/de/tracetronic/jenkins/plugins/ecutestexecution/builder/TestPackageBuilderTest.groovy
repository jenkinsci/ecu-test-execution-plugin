package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.Specification

class TestPackageBuilderTest extends Specification {

    def 'Test Get Artifact Name'() {
        given:
            TestPackageBuilder builder = new TestPackageBuilder(null, null, null, null, null, null)

        expect:
            builder.getTestArtifactName() == 'package'
    }

    def 'Test Null Values'() {
        given:
            new TestPackageBuilder(null, null, null, null, null, null)

        expect:
            TestPackageBuilder.testCasePath == null
            TestPackageBuilder.testConfig == null
            TestPackageBuilder.executionConfig == null
            TestPackageBuilder.context == null
    }

    def 'Test Default Values'() {
        given:
            final StepContext context = Mock()
            final String file = 'testFile'
            final TestConfig testConfig = new TestConfig()
            final ExecutionConfig executionConfig = new ExecutionConfig()
            final PackageConfig packageConfig = new PackageConfig(null)
            final AnalysisConfig analysisConfig = new AnalysisConfig()
            TestPackageBuilder builder = new TestPackageBuilder(file, testConfig,
                    executionConfig, context, packageConfig, analysisConfig)

        when:
            ExecutionOrder order = builder.getExecutionOrder()

        then:
            assertBuilder()
            assertExecutionOrder(order)
    }

    def assertBuilder()  {
        TestPackageBuilder.testCasePath != null
        TestPackageBuilder.testConfig != null
        TestPackageBuilder.executionConfig != null
        TestPackageBuilder.context != null
    }

    def assertExecutionOrder(ExecutionOrder order) {
        order.additionalSettings != null
        order.getTestCasePath() == 'testFile'
        order.getTbcPath() == ''
        order.getTcfPath() == ''
        order.getConstants() != null
        order.getExecutionId() == ''
    }
}