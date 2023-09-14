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
    private final ArrayList<String> toolInstallations

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
            if (ProcessUtil.killProcesses(toolInstallations, timeout)) {
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
        listener.logger.println("Stop TraceTronic tool instances.")
        if (ProcessUtil.killTTProcesses(timeout)) {
            listener.logger.println("Stopped TraceTronic tools successfully.")
        } else {
            throw new TimeoutException("Timeout of ${timeout} seconds exceeded for stopping TraceTronic tools!")
        }
    }

    private ArrayList<String> getToolInstallationsOnNode() {
        /**
         * This method gets the executable names of the tool installations on the node given by the context.
         * @return list of the executable names of the ECU-TEST installations on the respective node (can also be
         * TRACE-CHECK executables)
         */
        Computer computer = context.get(Launcher).getComputer()
        Node node = computer?.getNode()
        EnvVars envVars = context.get(EnvVars)
        TaskListener listener = context.get(TaskListener)
        if (node) {
            return ETInstallation.getAllExecutableNames(envVars, node, listener)
        }
        return []
    }
}
