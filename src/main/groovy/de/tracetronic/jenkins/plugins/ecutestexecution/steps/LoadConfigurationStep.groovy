package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.FormValidation
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.*
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

class LoadConfigurationStep extends Step {

    private String tbcPath
    private String tcfPath
    private boolean startConfig
    private List<Constant> constants

    @DataBoundConstructor
    LoadConfigurationStep() {
        this.tcfPath = ""
        this.tbcPath = ""
        this.startConfig = true
        this.constants = []
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

    private static List<Constant> removeEmptyConstants(List<Constant> constants) {
        return constants.findAll { constant -> StringUtils.isNotBlank(constant.getLabel()) }
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L

        private final transient LoadConfigurationStep step

        protected Execution(LoadConfigurationStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            return null
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
            return 'Loads ecu.test configurations'
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
