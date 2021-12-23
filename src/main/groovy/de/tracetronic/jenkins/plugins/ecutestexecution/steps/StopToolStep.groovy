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
import hudson.Launcher
import hudson.model.Computer
import hudson.model.Node
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

    @DataBoundConstructor
    StopToolStep(String toolName) {
        super()
        this.toolName = StringUtils.trimToEmpty(toolName)
        this.timeout = DEFAULT_TIMEOUT
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

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private final transient StopToolStep step

        Execution(StopToolStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            return getContext().get(Launcher.class).getChannel().call(
                    new ExecutionCallable(getToolInstallation(), step.timeout,
                            context.get(EnvVars.class), context.get(TaskListener.class)))
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

        private final ETInstallation installation
        private final int timeout
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(ETInstallation installation, int timeout, EnvVars envVars, TaskListener listener) {
            super()
            this.installation = installation
            this.timeout = timeout
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        Void call() throws IOException {
            String toolName = installation.getName()
            listener.logger.println("Stopping ${toolName}...")
            if (ProcessUtil.killProcess(ETInstallation.getExeFileName(), timeout)) {
                listener.logger.println("${toolName} stopped successfully.")
            } else {
                throw new TimeoutException("Timeout of ${this.timeout} seconds exceeded for stopping ${toolName}!")
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
