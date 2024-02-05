/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.util.EnvVarUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil

import hudson.EnvVars
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tools.ToolInstallation
import hudson.util.ArgumentListBuilder
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

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class StartToolStep extends Step {

    private static final int DEFAULT_TIMEOUT = 60

    private final String toolName
    private String workspaceDir
    private String settingsDir
    private int timeout
    private boolean keepInstance
    private boolean stopUndefinedTools

    @DataBoundConstructor
    StartToolStep(String toolName) {
        super()
        this.toolName = StringUtils.trimToEmpty(toolName)
        this.workspaceDir = ''
        this.settingsDir = ''
        this.timeout = DEFAULT_TIMEOUT
        this.keepInstance = false
        this.stopUndefinedTools = true
    }

    String getToolName() {
        return toolName
    }

    String getWorkspaceDir() {
        return workspaceDir
    }

    @DataBoundSetter
    void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = StringUtils.trimToEmpty(workspaceDir)
    }

    String getSettingsDir() {
        return settingsDir
    }

    @DataBoundSetter
    void setSettingsDir(String settingsDir) {
        this.settingsDir = StringUtils.trimToEmpty(settingsDir)
    }

    int getTimeout() {
        return timeout
    }

    @DataBoundSetter
    void setTimeout(int timeout) {
        this.timeout = timeout < 0 ? 0 : timeout
    }

    boolean getKeepInstance() {
        return keepInstance
    }

    @DataBoundSetter
    void setKeepInstance(boolean keepInstance) {
        this.keepInstance = keepInstance
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

    static class Execution extends SynchronousNonBlockingStepExecution<Void> implements Serializable {

        private static final long serialVersionUID = 1L

        private final transient StartToolStep step

        Execution(StartToolStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            try {
                EnvVars envVars = context.get(EnvVars.class)
                FilePath workspace = context.get(FilePath.class)
                String expWorkspaceDir = EnvVarUtil.expandVar(step.workspaceDir, envVars, workspace.getRemote())
                String expSettingsDir = EnvVarUtil.expandVar(step.settingsDir, envVars, workspace.getRemote())

                expWorkspaceDir = PathUtil.makeAbsoluteInPipelineHome(expWorkspaceDir, context)
                expSettingsDir = PathUtil.makeAbsoluteInPipelineHome(expSettingsDir, context)

                checkWorkspace(expWorkspaceDir, expSettingsDir)

                return context.get(Launcher.class).getChannel().call(
                        new ExecutionCallable(ETInstallation.getToolInstallationForMaster(context, step.toolName),
                                expWorkspaceDir, expSettingsDir, step.timeout, step.keepInstance,
                                step.stopUndefinedTools, envVars, context.get(TaskListener.class)))
            } catch (Exception e) {
                context.get(TaskListener.class).error(e.message)
                context.get(Run.class).setResult(Result.FAILURE)
            }
        }

        private void checkWorkspace(String workspaceDir, String settingsDir)
                throws IOException, InterruptedException, IllegalArgumentException {
            FilePath workspacePath = new FilePath(context.get(Launcher.class).getChannel(), workspaceDir)
            if (!workspacePath.exists()) {
                throw new IllegalArgumentException(
                        "ecu.test workspace directory at ${workspacePath.getRemote()} does not exist!")
            }

            FilePath settingsPath = new FilePath(context.get(Launcher.class).getChannel(), settingsDir)
            if (!settingsPath.exists()) {
                settingsPath.mkdirs()
                def listener = context.get(TaskListener.class)
                listener.logger.println("ecu.test settings directory created at ${settingsPath.getRemote()}.")
            }
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<Void, IOException> {

        private static final long serialVersionUID = 1L

        private final ETInstallation installation
        private final String workspaceDir
        private final String settingsDir
        private final int timeout
        private final boolean keepInstance
        private final boolean stopUndefinedTools
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(ETInstallation installation, String workspaceDir, String settingsDir, int timeout,
                          boolean keepInstance, boolean stopUndefinedTools, EnvVars envVars, TaskListener listener) {
            super()
            this.installation = installation
            this.workspaceDir = workspaceDir
            this.settingsDir = settingsDir
            this.timeout = timeout
            this.keepInstance = keepInstance
            this.stopUndefinedTools = stopUndefinedTools
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        Void call() throws IOException {
            String toolName = installation.getName()
            if (keepInstance) {
                listener.logger.println("Re-using running instance ${toolName}...")
                connectTool(toolName)
            } else {
                if (stopUndefinedTools) {
                    listener.logger.println("Stop tracetronic tool instances.")
                    if (ProcessUtil.killTTProcesses(timeout)) {
                        listener.logger.println("Stopped tracetronic tools successfully.")
                    } else {
                        throw new TimeoutException("Timeout of ${this.timeout} seconds exceeded for stopping tracetronic tools!")
                    }
                }
                listener.logger.println("Starting ${toolName}...")
                checkLicense(toolName)
                startTool(toolName)
                connectTool(toolName)
                listener.logger.println("${toolName} started successfully.")
            }
            return null
        }

        /**
         * Checks whether the tool has a valid license.
         * @param the name of the tool, as defined in the Jenkins tool installation settings.
         */
        private void checkLicense(String toolName) {
            ArgumentListBuilder args = new ArgumentListBuilder()
            args.add(installation.exeFileOnNode.absolutePath)
            args.add("--startupAutomated=True")
            args.add("-q")
            Process process = new ProcessBuilder().command(args.toCommandArray()).start()

            Callable<Integer> call = new Callable<Integer>() {
                Integer call() throws Exception {
                    if (timeout <= 0) {
                        process.waitFor()
                    } else {
                        process.waitFor(timeout, TimeUnit.SECONDS)
                    }
                    return process.exitValue()
                }
            }
            Future<Integer> future = Executors.newSingleThreadExecutor().submit(call)
            try {
                int exitCode
                if (timeout <= 0) {
                    exitCode = future.get()
                } else {
                    exitCode = future.get(timeout, TimeUnit.SECONDS)
                }
                if (exitCode != 0) {
                    throw new IllegalStateException("No valid license found for ${toolName}!")
                }
            } catch (TimeoutException ignored) {
                process.destroy()
                throw new TimeoutException(
                        "Timeout of ${this.timeout} seconds exceeded for checking license of ${toolName}!")
            }
        }

        /**
         * Starts the tool (ecu.test or trace.check) with CLI parameters.
         * @param toolName the name of the tool, as defined in the Jenkins tool installation settings.
         * @throws IllegalStateException
         */
        private void startTool(String toolName) throws IllegalStateException {
            ArgumentListBuilder args = new ArgumentListBuilder()
            args.add(installation.exeFileOnNode.absolutePath)
            args.add('--workspaceDir', workspaceDir)
            args.add('-s', settingsDir)
            args.add('--startupAutomated=True')
            listener.logger.println(args.toString())

            Process process = new ProcessBuilder().command(args.toCommandArray()).start()

            boolean isStarted = false
            long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
            while (timeout <= 0 || System.currentTimeMillis() < endTimeMillis) {
                if ((isStarted = process.isAlive())) {
                    break
                } else {
                    Thread.sleep(1000)
                }
            }

            if (!isStarted) {
                throw new TimeoutException("Timeout of ${this.timeout} seconds exceeded for starting ${toolName}!")
            }
        }

        /**
         * Checks whether the REST API of the tool is available.
         * @param toolName the name of the tool, as defined in the Jenkins tool installation settings.
         */
        private void connectTool(String toolName) {
            try {
                 RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'), timeout)
            } catch (ApiException e) {
                throw new TimeoutException("Timeout of ${this.timeout} seconds exceeded for connecting to ${toolName}!")
            }
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
            'ttStartTool'
        }

        @Override
        String getDisplayName() {
            '[TT] Start an ecu.test or trace.check instance'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(EnvVars.class, Launcher.class, TaskListener.class)
        }

        FormValidation doCheckTimeout(@QueryParameter int value) {
            return ValidationUtil.validateTimeout(value)
        }

        FormValidation doCheckWorkspaceDir(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }

        FormValidation doCheckSettingsDir(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }
    }
}
