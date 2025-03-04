/*
 * Copyright (c) 2021-2024 tracetronic GmbH
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

class PublishConfig extends AbstractDescribableImpl<PublishConfig> implements Serializable {

    public static final int DEFAULT_TIMEOUT = 3600

    private static final long serialVersionUID = 1L

    private int timeout
    private boolean allowMissing
    private boolean keepAll
    private boolean failOnError


    @DataBoundConstructor
    PublishConfig() {
        this.timeout = DEFAULT_TIMEOUT
        this.allowMissing = false
        this.keepAll = true
        this.failOnError = true
    }

    PublishConfig(PublishConfig config) {
        this.timeout = config.getTimeout()
        this.allowMissing = config.getAllowMissing()
        this.keepAll = config.getKeepAll()
        this.failOnError = config.getFailOnError()
    }

    int getTimeout() {
        return timeout
    }

    @DataBoundSetter
    void setTimeout(int timeout) {
        this.timeout = timeout < 0 ? 0 : timeout
    }

    boolean getAllowMissing() {
        return allowMissing
    }

    @DataBoundSetter
    void setAllowMissing(boolean allowMissing) {
        this.allowMissing = allowMissing
    }

    boolean getKeepAll() {
        return keepAll
    }

    @DataBoundSetter
    void setKeepAll(boolean keepAll) {
        this.keepAll = keepAll
    }

    boolean getFailOnError() {
        return failOnError
    }

    @DataBoundSetter
    void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError
    }

    @Extension
    static class DescriptorImpl extends Descriptor<PublishConfig> {

        static int getDefaultTimeout() {
            DEFAULT_TIMEOUT
        }

        @Override
        String getDisplayName() {
            'Publishing Configuration'
        }

        FormValidation doCheckTimeout(@QueryParameter int value) {
            return ValidationUtil.validateTimeout(value)
        }
    }
}
