package de.tracetronic.jenkins.plugins.ecutestexecution.security

import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import jenkins.util.Timer

import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


abstract class ControllerToAgentCallableWithTimeout<V, T extends Throwable> extends MasterToSlaveCallable<V, T> {

    private long timeout
    private final TaskListener listener

    ControllerToAgentCallableWithTimeout(long timeout, TaskListener listener){
        this.timeout = timeout
        this.listener = listener
    }

    abstract V execute() throws Exception
    abstract void cancel()

    /**
     * Call the execution of a step in the build and return the results
     * Cancels the execution of the step upon reaching the given timeout by setting the apiClient's variable
     * executionTimedOut to true
     * @return V
     */
    @Override
    V call() throws T {
        try {
            ScheduledExecutorService exe = Timer.get()
            exe.schedule({ cancel() } as Callable, timeout, TimeUnit.SECONDS)
            execute()
        } catch (Exception e){
            if (e instanceof TimeoutException){
                listener.logger.println("Timeout: step execution took longer than ${timeout} seconds")
                listener.logger.flush()
            }
            throw e
        }
    }
}
