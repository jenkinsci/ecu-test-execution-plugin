/*
 * Copyright (c) 2024-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.security


import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import jenkins.util.Timer
import org.jenkinsci.plugins.workflow.steps.StepContext

import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class ControllerToAgentCallableWithTimeout<V, E extends Exception> extends MasterToSlaveCallable<V, E> {

    private long timeout
    protected final TaskListener listener

    ControllerToAgentCallableWithTimeout(long timeout, TaskListener listener) {
        this.timeout = timeout
        this.listener = listener
    }

    ControllerToAgentCallableWithTimeout(long timeout, StepContext context) {
        this.timeout = timeout
        this.listener = context.get(TaskListener.class)
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
    V call() throws E {
        try {
            ScheduledExecutorService exe = Timer.get()
            V result
            if (timeout != 0) {
                exe.schedule({ !result ? cancel() : { return } } as Callable, timeout, TimeUnit.SECONDS)
            }
            result = execute()
        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                listener.error("Execution has exceeded the configured timeout of ${timeout} seconds")
                listener.logger.flush()
            }
            throw e
        }
    }
}
