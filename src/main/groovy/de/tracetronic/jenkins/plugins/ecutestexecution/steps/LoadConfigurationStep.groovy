/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ConfigurationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.LoadConfigurationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.StopToolOptions
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.model.ToolInstallations
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.FormValidation
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.*
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import javax.annotation.Nonnull
import java.time.Duration

/**
 * Step providing the loading of ecu.test configurations.
 */
class LoadConfigurationStep extends Step {

    private String tbcPath
    private String tcfPath
    private boolean startConfig
    private List<Constant> constants
    @Nonnull
    private StopToolOptions stopOptions

    /**
     * Instantiates a new [LoadConfigurationStep].
     * @param tbcPath The tbc file to load
     * @param tcfPath The tcf file to load
     */
    @DataBoundConstructor
    LoadConfigurationStep(String tbcPath, String tcfPath) {
        super()
        this.tbcPath = tbcPath
        this.tcfPath = tcfPath
        this.startConfig = true
        this.constants = []
        this.stopOptions = new StopToolOptions()
    }

    String getTbcPath() {
        return tbcPath
    }

    @DataBoundSetter
    void setTbcPath(String tbcPath) {
        this.tbcPath = tbcPath
    }

    String getTcfPath() {
        return tcfPath
    }

    @DataBoundSetter
    void setTcfPath(String tcfPath) {
        this.tcfPath = tcfPath
    }

    boolean getStartConfig() {
        return startConfig
    }

    @DataBoundSetter
    void setStartConfig(boolean startConfig) {
        this.startConfig = startConfig
    }

    List<Constant> getConstants() {
        return constants == null ? [] : new ArrayList<>(constants)
    }

    @DataBoundSetter
    void setConstants(List<Constant> constants) {
        this.constants = constants ? removeEmptyConstants(constants) : []
    }

    @Nonnull
    StopToolOptions getStopOptions() {
        return new StopToolOptions(stopOptions)
    }

    @DataBoundSetter
    void setStopOptions(StopToolOptions stopOptions) {
        this.stopOptions = stopOptions ?: new StopToolOptions()
    }

    private static List<Constant> removeEmptyConstants(List<Constant> constants) {
        return constants.findAll { constant -> StringUtils.isNotBlank(constant.getLabel()) }
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<LoadConfigurationResult> {

        private static final long serialVersionUID = 1L

        private final transient LoadConfigurationStep step

        protected Execution(LoadConfigurationStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected LoadConfigurationResult run() throws Exception {
            try {
                EnvVars envVars = context.get(EnvVars.class)
                String expTbcPath = envVars.expand(step.tbcPath)
                String expTcfPath = envVars.expand(step.tcfPath)
                List<Constant> expConstants = step.constants.collect { it -> it.expand(envVars) }

                TaskListener listener = context.get(TaskListener.class)
                ToolInstallations toolInstallations = new ToolInstallations(context)

                LoadConfigurationResult result
                try {
                    result = context.get(Launcher.class).getChannel().call(new LoadConfigurationCallable(expTbcPath, expTcfPath, expConstants, step.startConfig, step.stopOptions, toolInstallations, envVars, listener))
                } catch (Exception e) {
                    listener.logger.println(StringUtils.capitalize(e.getClass().getSimpleName()) +
                            " occurred during loading configuration with tbcPath '${expTbcPath}' and tcfPath '${expTcfPath}': ${e.getMessage()}")
                    result = new LoadConfigurationResult("ERROR", "Loading configuration failed!")
                }
                if (result.result == "ERROR") {
                    listener.logger.println("Loading configuration failed!")
                    context.get(Run.class).setResult(Result.FAILURE)
                }

                listener.logger.flush()

                return result
            } catch (Exception e) {
                throw new AbortException(e.getMessage())
            }
        }
    }

    private static final class LoadConfigurationCallable extends MasterToSlaveCallable<LoadConfigurationResult, Exception> {

        private static final long serialVersionUID = 1L
        private static final Duration STOP_TOOLS_TIMEOUT = Duration.ofHours(1)

        private final String tbcPath
        private final String tcfPath
        private final List<Constant> constants
        private final boolean startConfig
        private final StopToolOptions options
        private final ToolInstallations toolInstallations
        private final EnvVars envVars
        private final TaskListener listener

        LoadConfigurationCallable(String tbcPath, String tcfPath, List<Constant> constants,
                                  boolean startConfig, StopToolOptions options, ToolInstallations toolInstallations, EnvVars envVars, TaskListener taskListener) {
            this.tbcPath = tbcPath
            this.tcfPath = tcfPath
            this.constants = constants
            this.startConfig = startConfig
            this.options = options
            this.toolInstallations = toolInstallations
            this.envVars = envVars
            this.listener = taskListener
        }

        @Override
        LoadConfigurationResult call() throws Exception {
            listener.logger.println("Loading configuration with tbcPath: ${tbcPath}, tcfPath: ${tcfPath}, " +
                    "startConfig: ${startConfig}, constants: ${constants}")
            RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            ConfigurationOrder configurationOrder = new ConfigurationOrder(tbcPath, tcfPath, constants, startConfig)

            LoadConfigurationResult result = apiClient.loadConfiguration(configurationOrder)
            listener.logger.println(result.toString())

            if (result.result == "ERROR" && options.stopOnError) {
                toolInstallations.stopToolInstances((int) STOP_TOOLS_TIMEOUT.toSeconds())
                if (options.stopUndefinedTools) {
                    toolInstallations.stopTTInstances((int) STOP_TOOLS_TIMEOUT.toSeconds())
                }
            }

            listener.logger.flush()

            return result
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        @Override
        String getFunctionName() {
            return 'ttLoadConfig'
        }

        @Override
        String getDisplayName() {
            return '[TT] Load ecu.test configurations'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, Run.class, EnvVars.class, TaskListener.class)
        }

        FormValidation doCheckTbcPath(@QueryParameter String value) {
            return ValidationUtil.validateFileExtension(value, '.tbc')
        }

        FormValidation doCheckTcfPath(@QueryParameter final String value) {
            return ValidationUtil.validateFileExtension(value, '.tcf')
        }
    }
}
