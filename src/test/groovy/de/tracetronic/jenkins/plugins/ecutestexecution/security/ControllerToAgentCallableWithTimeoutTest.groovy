package de.tracetronic.jenkins.plugins.ecutestexecution.security

import hudson.model.TaskListener
import jenkins.util.Timer
import spock.lang.Specification
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ControllerToAgentCallableWithTimeoutTest extends Specification {

    def "should not schedule task when timeout is zero"() {
        given:
        def listener = Mock(TaskListener)
        def callable = new TestControllerToAgentCallable(0, listener)

        when:
        def result = callable.call()

        then:
        result == "Executed"
        0 * Timer.get().schedule(_, _, _)
    }

    def "should schedule task when timeout is non-zero"() {
        given:
        def listener = Mock(TaskListener)
        ScheduledExecutorService mockExecutor = Mock(ScheduledExecutorService)
        Timer.metaClass.static.get = { -> mockExecutor }
        def callable = new TestControllerToAgentCallable(5, listener)

        when:
        def result = callable.call()

        then:
        result == "Executed"
        1 * mockExecutor.schedule(_, 5, TimeUnit.SECONDS)
    }

    private static class TestControllerToAgentCallable extends ControllerToAgentCallableWithTimeout<String, Exception> {

        TestControllerToAgentCallable(long timeout, TaskListener listener) {
            super(timeout, listener)
        }

        @Override
        String execute() throws Exception {
            return "Executed"
        }

        @Override
        void cancel() {
            //
        }
    }
}
