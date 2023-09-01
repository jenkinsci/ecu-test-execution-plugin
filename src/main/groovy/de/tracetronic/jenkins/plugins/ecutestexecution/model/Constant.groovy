/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.model

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExpandableConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import hudson.util.FormValidation
import org.apache.commons.lang.StringUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.QueryParameter

class Constant extends AbstractDescribableImpl<Constant> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L

    private final String label
    private final String value

    @DataBoundConstructor
    Constant(String label, String value) {
        this.label = StringUtils.trimToEmpty(label)
        this.value = StringUtils.trimToEmpty(value)
    }

    Constant(Constant constant) {
        this.label = constant.getLabel()
        this.value = constant.getValue()
    }

    String getLabel() {
        return label
    }

    String getValue() {
        return value
    }

    @Override
    String toString() {
        "${label}=${value}"
    }

    @Override
    Constant expand(EnvVars envVars) {
        return new Constant(envVars.expand(label), envVars.expand(value))
    }

    @Extension
    static class DescriptorImpl extends Descriptor<Constant> {

        @Override
        String getDisplayName() {
            'Global Constant'
        }

        FormValidation doCheckLabel(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value, true)
        }

        FormValidation doCheckValue(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }
    }
}
