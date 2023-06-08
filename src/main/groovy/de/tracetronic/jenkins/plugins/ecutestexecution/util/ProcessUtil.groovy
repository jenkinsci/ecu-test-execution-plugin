/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.Functions
import hudson.util.ArgumentListBuilder

import java.util.concurrent.TimeUnit

final class ProcessUtil {

    private ProcessUtil() {
        throw new UnsupportedOperationException('Utility class')
    }

    /**
     * Kills this process and all the descendant processes that this process launched.
     *
     * @param taskName the task name of the process
     * @param timeout the maximum time to wait for process termination, 0 disabled timeout
     * @return {@code true} if process has exited in timeout, {@code false} otherwise
     */
    static boolean killProcess(String taskName, int timeout = 30) {
        ArgumentListBuilder args = new ArgumentListBuilder()
        println('Kill process' + taskName)
        /*if (Functions.isWindows()) {
            args.add('taskkill.exe')
            args.addTokenized('/f /im')
        } else {
            args.add('pkill')
        }
        args.add(taskName)

        Process process = new ProcessBuilder().command(args.toCommandArray()).start()
        if (timeout <= 0) {
            return process.waitFor() == 0
        } else {
            return process.waitFor(timeout, TimeUnit.SECONDS)
        }*/
    }

    /**
     * Kills all processes in the given list, and all their descendant processes, by calling {@link #killProcess
     * killProcess} method multiple times.
     * @param taskName the task name of the process
     * @param timeout the maximum time to wait for process termination, 0 disabled timeout
     * @return {@code true} if all processes have exited in timeout, {@code false} otherwise
     */
    static boolean killProcesses(ArrayList<String> taskNames, int timeout = 30) {
        boolean allExitedInTimeout = true
        taskNames.each {
            it ->
                if (!killProcess(it, timeout) && allExitedInTimeout) allExitedInTimeout = false
        }
        return allExitedInTimeout
    }
}
