package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import jenkins.util.Timer

import java.sql.Time
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


abstract class TimeoutMasterToSlaveCallable<V, T extends Throwable> extends MasterToSlaveCallable<V, T> {

    private long timeout
    private final TaskListener listener


    TimeoutMasterToSlaveCallable(long timeout,  TaskListener listener){
        this.timeout = timeout;
        this.listener = listener
    }

    abstract V execute() throws Exception

    @Override
    V call() throws T {
        ScheduledExecutorService exe = Timer.get()
        Future<V> future = exe.schedule({execute()} as Callable<V>,0,TimeUnit.SECONDS)
        try {
            return future.get(timeout, TimeUnit.SECONDS)
        } catch (Exception e) {
            if (e instanceof TimeoutException){
                future.cancel(true)
            }
            throw e
        }
    }
}
