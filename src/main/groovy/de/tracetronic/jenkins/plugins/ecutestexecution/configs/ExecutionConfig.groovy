/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import hudson.util.FormValidation
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

class ExecutionConfig extends AbstractDescribableImpl<ExecutionConfig> implements Serializable {

    public static final int DEFAULT_TIMEOUT = 3600

    private static final long serialVersionUID = 1L

    private int timeout
    private boolean stopOnError
    private boolean stopUndefinedTools
    private boolean executePackageCheck


    @DataBoundConstructor
    ExecutionConfig() {
        this.timeout = DEFAULT_TIMEOUT
        this.stopOnError = true
        this.stopUndefinedTools = true
        this.executePackageCheck = false
    }

    ExecutionConfig(ExecutionConfig config) {
        this.timeout = config.getTimeout()
        this.stopOnError = config.isStopOnError()
        this.stopUndefinedTools = config.getStopUndefinedTools()
        this.executePackageCheck = config.getExecutePackageCheck()
    }

    int getTimeout() {
        return timeout
    }

    @DataBoundSetter
    void setTimeout(int timeout) {
        this.timeout = timeout < 0 ? 0 : timeout
    }

    boolean isStopOnError() {
        return stopOnError
    }

    @DataBoundSetter
    void setStopOnError(boolean stopOnError) {
        this.stopOnError = stopOnError
    }

    boolean getExecutePackageCheck() {
        return executePackageCheck
    }

    @DataBoundSetter
    void setExecutePackageCheck(boolean executePackageCheck) {
        this.executePackageCheck = executePackageCheck
    }

    boolean getStopUndefinedTools() {
        return stopUndefinedTools
    }

    @DataBoundSetter
    void setStopUndefinedTools(boolean stopUndefinedTools) {
        this.stopUndefinedTools = stopUndefinedTools
    }

    @Extension
    static class DescriptorImpl extends Descriptor<ExecutionConfig> {

        static int getDefaultTimeout() {
            DEFAULT_TIMEOUT
        }

        @Override
        String getDisplayName() {
            'Execution Configuration'
        }

        FormValidation doCheckTimeout(@QueryParameter int value) {
            return ValidationUtil.validateTimeout(value)
        }
    }
}
