package de.tracetronic.jenkins.plugins.ecutestexecution.security

import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import jenkins.util.Timer

import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


abstract class TimeoutControllerToAgentCallable<V, T extends Throwable> extends MasterToSlaveCallable<V, T> {

    private long timeout
    private final TaskListener listener


    TimeoutControllerToAgentCallable(long timeout,  TaskListener listener){
        this.timeout = timeout
        this.listener = listener
    }

    abstract V execute() throws Exception

    /**
     * Call the execution of a step in the build and return the results
     * Cancels the execution of the step and throws TimeoutException upon reaching the given timeout.
     * The interrupt signal send by the cancel needs to be handle by the individual thread executing the step.
     * @return V
     */
    @Override
    V call() throws T {
        ScheduledExecutorService exe = Timer.get()
        Future<V> future = exe.schedule({execute()} as Callable<V>,0,TimeUnit.SECONDS)

        try {
            if (timeout <= 0) {
                return future.get()
            } else {
                return future.get(timeout, TimeUnit.SECONDS)
            }
        } catch (Exception e) {
            if (e instanceof TimeoutException){
                listener.logger.println("Timeout: step execution took longer than ${timeout} seconds")
                listener.logger.flush()
                future.cancel(true)
            }
            throw e
        }
    }
}
