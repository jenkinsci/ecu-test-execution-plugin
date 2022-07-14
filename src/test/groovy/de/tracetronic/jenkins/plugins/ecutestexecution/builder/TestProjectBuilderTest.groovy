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
            new TestProjectBuilder(null, null, null, null)

        expect:
            TestProjectBuilder.testCasePath == null
            TestProjectBuilder.testConfig == null
            TestProjectBuilder.executionConfig == null
            TestProjectBuilder.context == null
    }

    def 'Test Default Values'() {
        given:
            final StepContext context = Mock()
            final String testCasePath = 'testFile'
            final TestConfig testConfig = new TestConfig()
            final ExecutionConfig executionConfig = new ExecutionConfig()
            new TestProjectBuilder(testCasePath, testConfig,
                executionConfig, context)

        when:
            ExecutionOrderBuilder executionOrderBuilder = new ExecutionOrderBuilder(testCasePath, testConfig)
            ExecutionOrder order = executionOrderBuilder.build()

        then:
            assertBuilder()
            assertExecutionOrder(order)
    }

    void assertBuilder()  {
        assert TestProjectBuilder.testCasePath == 'testFile'
        assert TestProjectBuilder.testConfig != null
        assert TestProjectBuilder.executionConfig != null
        assert TestProjectBuilder.context != null
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
