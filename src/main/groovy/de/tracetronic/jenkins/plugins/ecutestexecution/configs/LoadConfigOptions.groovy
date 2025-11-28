package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

class LoadConfigOptions extends AbstractDescribableImpl<LoadConfigOptions> implements Serializable {
    private static final long serialVersionUID = 1L

    private boolean stopOnError
    private boolean stopUndefinedTools

    @DataBoundConstructor
    LoadConfigOptions() {
        this.stopOnError = true
        this.stopUndefinedTools = true
    }

    LoadConfigOptions(LoadConfigOptions options) {
        this.stopOnError = options.isStopOnError()
        this.stopUndefinedTools = options.isStopUndefinedTools()
    }

    boolean isStopOnError() {
        return stopOnError
    }

    @DataBoundSetter
    void setStopOnError(boolean stopOnError) {
        this.stopOnError = stopOnError
    }

    boolean isStopUndefinedTools() {
        return stopUndefinedTools
    }

    @DataBoundSetter
    void setStopUndefinedTools(boolean stopUndefinedTools) {
        this.stopUndefinedTools = stopUndefinedTools
    }

    @Extension
    static class DescriptorImpl extends Descriptor<LoadConfigOptions> {

        @Override
        String getDisplayName() {
            'Options'
        }
    }

}
