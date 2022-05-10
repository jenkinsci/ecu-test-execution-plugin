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
            final String file = 'testFile'
            final TestConfig testConfig = new TestConfig()
            final ExecutionConfig executionConfig = new ExecutionConfig()
            TestProjectBuilder builder = new TestProjectBuilder(file, testConfig,
                executionConfig, context)

        when:
            ExecutionOrder order = builder.getExecutionOrder()

        then:
            assertBuilder()
            assertExecutionOrder(order)
    }

    def assertBuilder()  {
        TestProjectBuilder.testCasePath != null
        TestProjectBuilder.testConfig != null
        TestProjectBuilder.executionConfig != null
        TestProjectBuilder.context != null
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
