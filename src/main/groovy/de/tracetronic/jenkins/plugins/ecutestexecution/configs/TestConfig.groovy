/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.model.LabeledValue
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import hudson.util.FormValidation
import org.apache.commons.lang.StringUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

class TestConfig extends AbstractDescribableImpl<TestConfig> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L

    private String tbcPath
    private String tcfPath
    private boolean forceConfigurationReload
    private List<LabeledValue> constants

    @DataBoundConstructor
    TestConfig() {
        this.tbcPath = ''
        this.tcfPath = ''
        this.forceConfigurationReload = false
        this.constants = []
    }

    String getTbcPath() {
        return tbcPath
    }

    @DataBoundSetter
    void setTbcPath(String tbcPath) {
        this.tbcPath = StringUtils.trimToEmpty(tbcPath)
    }

    String getTcfPath() {
        return tcfPath
    }

    @DataBoundSetter
    void setTcfPath(String tcfPath) {
        this.tcfPath = StringUtils.trimToEmpty(tcfPath)
    }

    boolean isForceConfigurationReload() {
        return forceConfigurationReload
    }

    @DataBoundSetter
    void setForceConfigurationReload(boolean forceConfigurationReload) {
        this.forceConfigurationReload = forceConfigurationReload
    }

    List<LabeledValue> getConstants() {
        return constants
    }

    @DataBoundSetter
    void setConstants(List<LabeledValue> constants) {
        this.constants = constants ? removeEmptyConstants(constants) : []
    }

    @Override
    String toString() {
        """
        -> tbcPath: ${tbcPath}
        -> tcfPath: ${tcfPath}
        -> forceConfigurationReload: ${forceConfigurationReload}
        -> constants: ${constants.each { it }}
        """.stripIndent().trim()
    }

    @Override
    TestConfig expand(EnvVars envVars) {
        TestConfig expConfig = new TestConfig()
        expConfig.setTbcPath(envVars.expand(tbcPath))
        expConfig.setTcfPath(envVars.expand(tcfPath))
        expConfig.setConstants(constants.collect { constant -> constant.expand(envVars) })
        return expConfig
    }

    private static List<LabeledValue> removeEmptyConstants(List<LabeledValue> constants) {
        return constants.findAll { constant -> StringUtils.isNotBlank(constant.getLabel()) }
    }

    @Extension
    static class DescriptorImpl extends Descriptor<TestConfig> {

        @Override
        String getDisplayName() {
            'TestConfig'
        }

        FormValidation doCheckTbcPath(@QueryParameter String value) {
            return ValidationUtil.validateConfigFile(value, '.tbc')
        }

        FormValidation doCheckTcfPath(@QueryParameter final String value) {
            return ValidationUtil.validateConfigFile(value, '.tcf')
        }
    }
}
