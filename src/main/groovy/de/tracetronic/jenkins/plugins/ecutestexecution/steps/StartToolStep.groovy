/*
 * Copyright (c) 2021-2026 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.model.StartToolResult
import de.tracetronic.jenkins.plugins.ecutestexecution.util.EnvVarUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.*
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tools.ToolInstallation
import hudson.util.ArgumentListBuilder
import hudson.util.FormValidation
import hudson.util.ListBoxModel
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.jenkinsci.plugins.workflow.steps.*
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import java.nio.file.Path
import java.util.concurrent.TimeoutException

class StartToolStep extends Step {

    private static final int DEFAULT_TIMEOUT = 60
    static final int MAX_WINDOWS_FILE_PATH_LENGTH = 260

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

    static class Execution extends SynchronousNonBlockingStepExecution<StartToolResult> implements Serializable {

        private static final long serialVersionUID = 1L

        private final transient StartToolStep step

        Execution(StartToolStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected StartToolResult run() throws Exception {
            try {
                EnvVars envVars = context.get(EnvVars.class)
                FilePath workspace = context.get(FilePath.class)
                TaskListener listener = context.get(TaskListener.class)

                String agentWorkspace = workspace.getRemote()

                String expWorkspaceDir = EnvVarUtil.expandVar(step.workspaceDir, envVars, agentWorkspace)
                String expSettingsDir = EnvVarUtil.expandVar(step.settingsDir, envVars, agentWorkspace)

                expWorkspaceDir = PathUtil.makeAbsoluteInPipelineHome(expWorkspaceDir, context)
                expSettingsDir = PathUtil.makeAbsoluteInPipelineHome(expSettingsDir, context)

                checkWorkspace(expWorkspaceDir, expSettingsDir)

                StartToolResult result = context.get(Launcher.class).getChannel().call(
                        new ExecutionCallable(ETInstallation.getToolInstallationForMaster(context, step.toolName),
                                expWorkspaceDir, expSettingsDir, step.timeout, step.keepInstance,
                                step.stopUndefinedTools, agentWorkspace, envVars, listener))
                listener.logger.println(result.toString())
                listener.logger.flush()
                return result

            } catch (Exception e) {
                context.get(Run.class).setResult(Result.FAILURE)
                // there is no friendly option to stop the step execution without an exception
                Exception exception = new AbortException(e.getMessage())
                exception.addSuppressed(e)
                throw exception
            }
        }

        private void checkWorkspace(String workspaceDir, String settingsDir)
                throws IOException, InterruptedException, IllegalArgumentException {
            FilePath workspacePath = new FilePath(context.get(Launcher.class).getChannel(), workspaceDir)
            if (!workspacePath.exists()) {
                throw new AbortException(
                        "ecu.test workspace directory at ${workspacePath.getRemote()} does not exist! " +
                                "Please ensure that the path is correctly set and it refers to the desired directory.")
            }

            FilePath settingsPath = new FilePath(context.get(Launcher.class).getChannel(), settingsDir)
            if (!settingsPath.exists()) {
                settingsPath.mkdirs()
                def listener = context.get(TaskListener.class)
                listener.logger.println("ecu.test settings directory created at ${settingsPath.getRemote()}.")
            }
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<StartToolResult, TimeoutException> {

        private static final long serialVersionUID = 1L

        private final ETInstallation installation
        private final String ecuTestWorkspaceDir
        private final String settingsDir
        private final int timeout
        private final boolean keepInstance
        private final boolean stopUndefinedTools
        private final String agentWorkspace
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(ETInstallation installation, String ecuTestWorkspaceDir, String settingsDir, int timeout,
                          boolean keepInstance, boolean stopUndefinedTools, String agentWorkspace, EnvVars envVars,
                          TaskListener listener) {
            super()
            this.installation = installation
            this.ecuTestWorkspaceDir = ecuTestWorkspaceDir
            this.settingsDir = settingsDir
            this.timeout = timeout
            this.keepInstance = keepInstance
            this.stopUndefinedTools = stopUndefinedTools
            this.agentWorkspace = agentWorkspace
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        StartToolResult call() throws TimeoutException {
            try {
                String toolName = installation.getName()
                if (keepInstance) {
                    listener.logger.println("Re-using running instance ${toolName}...")
                    if (!checkToolConnection()) {
                        throw new AbortException(
                                "Timeout of ${this.timeout} seconds exceeded for re-using tracetronic tools! " +
                                        "Please ensure that tracetronic tools are not already stopped or " +
                                        "blocked by another process.")
                    }
                } else {
                    if (stopUndefinedTools) {
                        listener.logger.println("Stop tracetronic tool instances.")
                        if (ProcessUtil.killTTProcesses(timeout)) {
                            listener.logger.println("Stopped tracetronic tools successfully.")
                        } else {
                            throw new AbortException(
                                    "Timeout of ${this.timeout} seconds exceeded for stopping tracetronic tools! " +
                                            "Please ensure that tracetronic tools are not already stopped or " +
                                            "blocked by another process.")
                        }
                    }
                    listener.logger.println("Starting ${toolName}...")
                    startTool(toolName)
                    listener.logger.println("${toolName} started successfully.")
                }
                return new StartToolResult(installation.getName(), installation.exeFileOnNode.absolutePath.toString(), ecuTestWorkspaceDir, settingsDir)

            } catch (Exception e) {
                throw new AbortException(e.getMessage())
            }
        }

        /**
         * Starts the tool (ecu.test or trace.check) with CLI parameters.
         * @param toolName the name of the tool, as defined in the Jenkins tool installation settings.
         * @throws AbortException
         */
        private void startTool(String toolName) throws IllegalStateException {
            ArgumentListBuilder args = new ArgumentListBuilder()
            args.add(installation.exeFileOnNode.absolutePath)
            args.add('--workspaceDir', ecuTestWorkspaceDir)
            args.add('-s', settingsDir)
            args.add('--startupAutomated=True')
            listener.logger.println(args.toString())

            ensureDirectoryExists(agentWorkspace)

            File stdoutLogFile = createLogFile(this.agentWorkspace, toolName, '_tool_out.log')
            File stderrLogFile = createLogFile(this.agentWorkspace, toolName, '_tool_err.log')

            listener.logger.println("ecu.test stdout: ${stdoutLogFile.absolutePath}")
            listener.logger.println("ecu.test stderr: ${stderrLogFile.absolutePath}")

            Process process = new ProcessBuilder()
                    .command(args.toCommandArray())
                    .redirectError(stderrLogFile)
                    .redirectOutput(stdoutLogFile)
                    .start()

            boolean isConnected = checkToolConnection()
            int exitCode = 0
            if (!isConnected) {
                try {
                    exitCode = process.exitValue()
                } catch (IllegalThreadStateException ignore) {
                    process.destroy()
                    throw new AbortException(
                            "Timeout of ${this.timeout} seconds exceeded for connecting to ${toolName}! " +
                                    "Please ensure the tool is correctly configured and consider restarting it.")
                }
            } else {
                return
            }

            if (exitCode == 99) {
                throw new AbortException("No valid license found for ${toolName}! " +
                        "Please ensure the license is not expired or corrupted.")
            }

            throw new AbortException(
                    "${toolName} did not start correctly and stopped with exit code ${exitCode} " +
                            "within the timeout of ${timeout} seconds.")
        }

        /**
         * Ensures that the target directory exists and can be used for log output.
         */
        private void ensureDirectoryExists(String directoryPath) {
            File directory = new File(directoryPath)
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    throw new AbortException("Path ${directory.absolutePath} exists but is not a directory.")
                }
                return
            }

            listener.logger.println("Agent workspace directory does not exist. Creating: ${directory.absolutePath}")
            if (!directory.mkdirs() && !directory.exists()) {
                throw new AbortException("Could not create agent workspace directory at ${directory.absolutePath}.")
            }
            listener.logger.println("Created agent workspace directory: ${directory.absolutePath}")
        }

        /**
         * Checks whether the REST API of the tool is available.
         * @return true if REST API is available, false if not
         */
        private boolean checkToolConnection() {
            try {
                return RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'),
                        envVars.get('ET_API_PORT'), timeout) instanceof RestApiClient
            } catch (ApiException ignore) {
                return false
            }
        }
    }

    /**
     * Creates a File with a sanitized filename based on the toolName.
     * @param directory The directory to create the file in
     * @param rawToolName The toolName provided by the user
     * @param suffix The suffix to add to the sanitized toolName
     * @return {@link File} with sanitized filename
     */
    static File createLogFile(String directory, String rawToolName, String suffix) {
        String safeToolName = sanitizeForFilename(rawToolName, 'tool')

        File file = Path.of(directory).resolve("${safeToolName}${suffix}").toFile()

        if (SystemUtils.IS_OS_WINDOWS) {
            String absolutePath = file.absolutePath
            if (absolutePath.length() > MAX_WINDOWS_FILE_PATH_LENGTH) {
                int charactersToShorten = absolutePath.length() - MAX_WINDOWS_FILE_PATH_LENGTH
                String truncatedToolName = safeToolName.substring(0, Math.max(0, safeToolName.length() - charactersToShorten))
                file = Path.of(directory).resolve("${truncatedToolName}${suffix}").toFile()
            }
        }

        return file
    }

    /**
     * Sanitizes a raw name to be used as a filename.
     * @param rawName The raw name
     * @return The name sanitized to be used as filename
     */
    static String sanitizeForFilename(String rawName, String fallback) {
        String name = StringUtils.trimToEmpty(rawName)
        // Replace characters that are invalid in Windows file names and can be interpreted as path syntax.
        name = name.replaceAll($/[<>:"/\\|?*]/$, '_')
        // Replace ASCII control characters (including DEL) to avoid invalid or non-printable file names.
        name = name.replaceAll(/[\x00-\x1F\x7F]/, '_')
        // Normalize whitespace to underscores to keep names readable and shell-friendly.
        name = name.replaceAll(/\s+/, '_')
        // Neutralize repeated dots so traversal-like inputs cannot survive as meaningful segments.
        name = name.replaceAll(/\.\.+/, '_')
        // Trim trailing dot/space because Windows does not allow them at the end of a file name.
        name = name.replaceAll(/[. ]+$/, '')
        // Collapse duplicate underscores introduced by previous replacements.
        name = name.replaceAll(/_+/, '_')
        name = StringUtils.trimToEmpty(name)

        // If no letter (Unicode characters are allowed) or digit is left, use fallback
        if (!containsLetterOrDigit(name)) {
            name = fallback
        }

        // Sanitize unallowed windows filenames
        if (name ==~ /(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/) {
            name = "_${name}"
        }

        return name
    }

    private static boolean containsLetterOrDigit(String value) {
        if (StringUtils.isEmpty(value)) {
            return false
        }

        int index = 0
        while (index < value.length()) {
            int codePoint = value.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint)) {
                return true
            }
            index += Character.charCount(codePoint)
        }

        return false
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
