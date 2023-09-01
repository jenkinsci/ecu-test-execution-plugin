/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import hudson.EnvVars
import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import org.apache.commons.lang.StringUtils
import org.kohsuke.stapler.DataBoundConstructor

class PackageConfig extends AbstractDescribableImpl<PackageConfig> implements ExpandableConfig, Serializable{

    private static final long serialVersionUID = 1L

    private List<PackageParameter> packageParameters

    @DataBoundConstructor
    PackageConfig(List<PackageParameter> packageParameters) {
        this.packageParameters = packageParameters ? removeEmptyParameters(packageParameters) : []
    }

    PackageConfig(PackageConfig config) {
        this.packageParameters = config.getPackageParameters()
    }

    List<PackageParameter> getPackageParameters() {
        return packageParameters.collect({new PackageParameter(it)})
    }

    @Override
    String toString() {
        "-> packageParameters: ${packageParameters.each { it }}".stripIndent().trim()
    }

    @Override
    PackageConfig expand(EnvVars envVars) {
        return new PackageConfig(packageParameters.collect { parameter -> parameter.expand(envVars) })
    }

    private static List<PackageParameter> removeEmptyParameters(List<PackageParameter> parameters) {
        return parameters.findAll { parameter -> StringUtils.isNotBlank(parameter.label) }
    }

    @Extension
    static class DescriptorImpl extends Descriptor<PackageConfig> {

        @Override
        String getDisplayName() {
            'PackageConfig'
        }
    }
}
