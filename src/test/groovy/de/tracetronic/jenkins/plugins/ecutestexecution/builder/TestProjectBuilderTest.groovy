package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.Specification

class TestProjectBuilderTest extends Specification {

    def 'Test Get Artifact Name'() {
        given:
            TestProjectBuilder builder = new TestProjectBuilder(null, null, null, null)

        expect:
            builder.getTestArtifactName() == 'project'
    }

    def 'Test Null Values'() {
        given:
            def tpb = new TestProjectBuilder(null, null, null, null)

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
            def tpb = new TestProjectBuilder(testCasePath, testConfig,
                executionConfig, context)

        when:
            ExecutionOrderBuilder executionOrderBuilder = new ExecutionOrderBuilder(testCasePath, testConfig)
            ExecutionOrder order = executionOrderBuilder.build()

        then:
            assertBuilder(tpb)
            assertExecutionOrder(order)
    }

    void assertBuilder(TestProjectBuilder tpb)  {
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
