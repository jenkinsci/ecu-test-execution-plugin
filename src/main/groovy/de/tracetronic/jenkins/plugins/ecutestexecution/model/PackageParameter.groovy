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

class PackageParameter extends AbstractDescribableImpl<PackageParameter> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L

    private final String label
    private final String value

    @DataBoundConstructor
    PackageParameter(String label, String value) {
        this.label = StringUtils.trimToEmpty(label)
        this.value = StringUtils.trimToEmpty(value)
    }

    PackageParameter(PackageParameter param) {
        this.label = param.getLabel()
        this.value = param.getValue()
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
    PackageParameter expand(EnvVars envVars) {
        return new PackageParameter(envVars.expand(label), envVars.expand(value))
    }

    @Extension
    static class DescriptorImpl extends Descriptor<PackageParameter> {

        @Override
        String getDisplayName() {
            'Package Parameter'
        }

        FormValidation doCheckLabel(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value, true)
        }

        FormValidation doCheckValue(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }
    }
}
