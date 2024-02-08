/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.model

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Computer
import hudson.model.Node
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.StepContext

import java.util.concurrent.TimeoutException

class ToolInstallations implements Serializable {

    private static final long serialVersionUID = 1L

    private final StepContext context
    private final TaskListener listener
    public final ArrayList<ETInstallation> toolInstallations

    ToolInstallations(StepContext context) {
        this.context = context
        this.listener = context.get(TaskListener.class)
        this.toolInstallations = getToolInstallationsOnNode()
    }

    /**
     * This method kills all processes defined in toolInstallations (e.g. different ECU-Test versions) and
     * prints the result
     * @return
     */
    void stopToolInstances(int timeout) throws TimeoutException {
        if (toolInstallations) {
            List<String> exeFileNames = toolInstallations.collect {it.exeFileOnNode.getName() }
            if (ProcessUtil.killProcesses(exeFileNames, timeout)) {
                listener.logger.println('-> Tools stopped successfully.')
            }
            else {
                throw new TimeoutException("Timeout of ${timeout} seconds exceeded for stopping tool!")
            }
        } else {
            listener.logger.println("No Tool Installations to stop were found. No processes killed.")
        }
    }

    /**
     * This methods kills all TT processes and prints the result
     * @return
     */
    void stopTTInstances(int timeout) throws TimeoutException {
        listener.logger.println("Stop tracetronic tool instances.")
        if (ProcessUtil.killTTProcesses(timeout)) {
            listener.logger.println("Stopped tracetronic tools successfully.")
        } else {
            throw new TimeoutException("Timeout of ${timeout} seconds exceeded for stopping tracetronic tools!")
        }
    }

    private ArrayList<ETInstallation> getToolInstallationsOnNode() {
        /**
         * This method gets the executable names of the tool installations on the node given by the context.
         * @return list of the executable names of the ecu.test installations on the respective node (can also be
         * trace.check executables)
         */
        Computer computer = context.get(Computer)
        Node node = computer?.getNode()
        EnvVars envVars = context.get(EnvVars)
        TaskListener listener = context.get(TaskListener)
        if (node) {
            return ETInstallation.getAllETInstallationsOnNode(envVars, node, listener)
        }
        return []
    }
}
