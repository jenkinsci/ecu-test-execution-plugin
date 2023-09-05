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
            def tpb = new TestPackageBuilder(null, null, null, null, null, null)

        expect:
            tpb.testCasePath == null
            tpb.testConfig == null
            tpb.executionConfig == null
            tpb.context == null
    }

    def 'Test Default Values'() {
        given:
            final StepContext context = Mock()
            final String testCasePath = 'testFile'
            final TestConfig testConfig = new TestConfig()
            final ExecutionConfig executionConfig = new ExecutionConfig()
            final PackageConfig packageConfig = new PackageConfig(null)
            final AnalysisConfig analysisConfig = new AnalysisConfig()
            def tpb = new TestPackageBuilder(testCasePath, testConfig,
                    executionConfig, context, packageConfig, analysisConfig)

        when:
            ExecutionOrderBuilder executionOrderBuilder = new ExecutionOrderBuilder(testCasePath, testConfig,
                    packageConfig, analysisConfig)
            ExecutionOrder order = executionOrderBuilder.build()

        then:
            assertBuilder(tpb)
            assertExecutionOrder(order)
    }

    void assertBuilder(TestPackageBuilder tpb)  {
        assert tpb.testCasePath == 'testFile'
        assert tpb.testConfig != null
        assert tpb.executionConfig != null
        assert tpb.context != null
    }

    void assertExecutionOrder(ExecutionOrder order) {
        assert order.additionalSettings != null
        assert order.getTestCasePath() == 'testFile'
        assert order.getTbcPath() == ''
        assert order.getTcfPath() == ''
        assert order.getConstants() != null
        assert order.getExecutionId() == ''
    }
}
