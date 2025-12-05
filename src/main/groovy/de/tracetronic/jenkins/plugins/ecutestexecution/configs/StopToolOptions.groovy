/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

/**
 * Options on how to handle running tools when a step fails.
 */
class StopToolOptions extends AbstractDescribableImpl<StopToolOptions> implements Serializable {
    private static final long serialVersionUID = 1L

    private boolean stopOnError
    private boolean stopUndefinedTools

    @DataBoundConstructor
    StopToolOptions() {
        this.stopOnError = true
        this.stopUndefinedTools = true
    }

    StopToolOptions(StopToolOptions options) {
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
    static class DescriptorImpl extends Descriptor<StopToolOptions> {

        @Override
        String getDisplayName() {
            'Options on how to handle running tools when a step fails'
        }
    }

}
