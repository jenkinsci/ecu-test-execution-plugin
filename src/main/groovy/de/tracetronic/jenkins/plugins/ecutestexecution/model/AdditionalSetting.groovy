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

class AdditionalSetting extends AbstractDescribableImpl<AdditionalSetting> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L

    private final String name
    private final String value

    @DataBoundConstructor
    AdditionalSetting(String name, String value) {
        this.name = StringUtils.trimToEmpty(name)
        this.value = StringUtils.trimToEmpty(value)
    }

    AdditionalSetting(AdditionalSetting setting) {
        this.name = setting.getName()
        this.value = setting.getValue()
    }

    String getName() {
        return name
    }

    String getValue() {
        return value
    }

    @Override
    String toString() {
        "${name}=${value}"
    }

    @Override
    AdditionalSetting expand(EnvVars envVars) {
        return new AdditionalSetting(envVars.expand(name), envVars.expand(value))
    }

    @Extension
    static class DescriptorImpl extends Descriptor<AdditionalSetting> {

        @Override
        String getDisplayName() {
            'Additional Setting'
        }

        FormValidation doCheckName(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value, true)
        }

        FormValidation doCheckValue(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }
    }
}
