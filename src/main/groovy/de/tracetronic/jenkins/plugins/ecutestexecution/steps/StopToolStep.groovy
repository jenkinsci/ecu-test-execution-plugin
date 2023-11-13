/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Functions
import hudson.Launcher
import hudson.model.Computer
import hudson.model.Node
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tools.ToolInstallation
import hudson.util.FormValidation
import hudson.util.ListBoxModel
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import java.util.concurrent.TimeoutException

class StopToolStep extends Step {

    private static final int DEFAULT_TIMEOUT = 30

    private final String toolName
    private int timeout
    private boolean stopUndefinedTools

    @DataBoundConstructor
    StopToolStep(String toolName) {
        super()
        this.toolName = StringUtils.trimToEmpty(toolName)
        this.timeout = DEFAULT_TIMEOUT
        this.stopUndefinedTools = true
    }

    String getToolName() {
        return toolName
    }

    int getTimeout() {
        return timeout
    }

    @DataBoundSetter
    void setTimeout(int timeout) {
        this.timeout = timeout < 0 ? 0 : timeout
    }

    boolean getStopUndefinedTools() {
        return stopUndefinedTools
    }

    @DataBoundSetter
    void setStopUndefinedTools(boolean stopUndefinedTools) {
        this.stopUndefinedTools = stopUndefinedTools
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L

        private final transient StopToolStep step

        Execution(StopToolStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            try {
                return getContext().get(Launcher.class).getChannel().call(
                        new ExecutionCallable(getToolInstallation(), step.timeout, step.stopUndefinedTools,
                                context.get(EnvVars.class), context.get(TaskListener.class)))
            } catch(Exception e) {
                context.get(TaskListener.class).error(e.message)
                context.get(Run.class).setResult(Result.FAILURE)
            }
        }

        private static ETInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(ETInstallation.DescriptorImpl.class)
        }

        private ETInstallation getToolInstallation() {
            String expToolName = context.get(EnvVars.class).expand(step.toolName)
            ETInstallation installation = getToolDescriptor().getInstallation(expToolName)

            if (installation) {
                // FIXME: deprecation
                Computer computer = getContext().get(Launcher).getComputer()
                final Node node = computer?.getNode()
                if (node) {
                    installation = installation.forNode(node, context.get(TaskListener.class))
                    installation = installation.forEnvironment(context.get(EnvVars.class))
                }
            } else {
                throw new IllegalArgumentException("Tool installation ${expToolName} is not configured for this node!")
            }

            return installation
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<Void, IOException> {

        private static final long serialVersionUID = 1L

        private final ETInstallation installation
        private final int timeout
        private final boolean stopUndefinedTools
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(ETInstallation installation, int timeout, boolean stopUndefinedTools, EnvVars envVars, TaskListener listener) {
            super()
            this.installation = installation
            this.timeout = timeout
            this.stopUndefinedTools = stopUndefinedTools
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        Void call() throws IOException {
            String toolName = installation.getName()
            if (toolName) {
                listener.logger.println("Stopping ${toolName}...")
                def exeFilePath = installation.exeFile.toString()
                def exeFileName = Functions.isWindows() ? exeFilePath.tokenize("\\")[-1] :
                        exeFilePath.tokenize("/")[-1]
                if (ProcessUtil.killProcess(exeFileName, timeout)) {
                    listener.logger.println("${toolName} stopped successfully.")
                } else {
                    throw new TimeoutException("Timeout of ${this.timeout} seconds exceeded for stopping ${toolName}!")
                }
            }

            if (stopUndefinedTools) {
                listener.logger.println("Stop TraceTronic tool instances.")
                if (ProcessUtil.killTTProcesses(timeout)) {
                    listener.logger.println("Stopped TraceTronic tools successfully.")
                } else {
                    throw new TimeoutException("Timeout of ${this.timeout} seconds exceeded for stopping TraceTronic tools!")
                }
            }

            return null
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        static int getDefaultTimeout() {
            return DEFAULT_TIMEOUT
        }

        static ListBoxModel doFillToolNameItems() {
            ListBoxModel model = new ListBoxModel()
            ETInstallation[] installations =
                    ToolInstallation.all().get(ETInstallation.DescriptorImpl.class).getInstallations()
            installations.each { installation ->
                model.add(installation.getName())
            }
            return model
        }

        @Override
        String getFunctionName() {
            'ttStopTool'
        }

        @Override
        String getDisplayName() {
            '[TT] Stop an ECU-TEST or TRACE-CHECK instance'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(EnvVars.class, Launcher.class, TaskListener.class)
        }

        FormValidation doCheckTimeout(@QueryParameter int value) {
            return ValidationUtil.validateTimeout(value)
        }
    }
}
