/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import hudson.util.FormValidation
import jline.internal.Nullable
import org.apache.commons.lang.StringUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

class TestConfig extends AbstractDescribableImpl<TestConfig> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L
    private static transient String configOption

    private String tbcPath
    private String tcfPath
    private boolean forceConfigurationReload
    private List<Constant> constants

    @DataBoundConstructor
    TestConfig() {
        this.tcfPath = null
        this.tbcPath = null
        this.constants = []
        this.forceConfigurationReload = false
    }

    TestConfig(TestConfig config) {
        this()

        configOption = getConfigOption()

        if(configOption == 'loadConfig') {
            this.tbcPath = config.getTbcPath()
            this.tcfPath = config.getTcfPath()
            this.constants = config.getConstants()
            this.forceConfigurationReload = config.isForceConfigurationReload()
        }
    }

    @Nullable
    String getTbcPath() {
        return tbcPath
    }

    @DataBoundSetter
    void setTbcPath(String tbcPath) {
        this.tbcPath = tbcPath || configOption == 'loadConfig' ? StringUtils.trimToEmpty(tbcPath) : null
    }

    @Nullable
    String getTcfPath() {
        return tcfPath
    }

    @DataBoundSetter
    void setTcfPath(String tcfPath) {
        this.tcfPath = tcfPath || configOption == 'loadConfig' ? StringUtils.trimToEmpty(tcfPath) : null
    }

    boolean isForceConfigurationReload() {
        return forceConfigurationReload
    }

    @DataBoundSetter
    void setForceConfigurationReload(boolean forceConfigurationReload) {
        this.forceConfigurationReload = forceConfigurationReload
    }

    List<Constant> getConstants() {
        return constants.collect({new Constant(it)})
    }

    @DataBoundSetter
    void setConstants(List<Constant> constants) {
        this.constants = constants ? removeEmptyConstants(constants) : []
    }

    @DataBoundSetter
    void setConfigOption(String value) {
        configOption = value
    }

    String getConfigOption() {
        return configOption ?: 'keepConfig'
    }

    @Override
    String toString() {
        """
        -> tbcPath: ${tbcPath}
        -> tcfPath: ${tcfPath}
        -> forceConfigurationReload: ${forceConfigurationReload}
        -> constants: ${constants.each { it }}
        -> configOption: ${configOption}
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

    private static List<Constant> removeEmptyConstants(List<Constant> constants) {
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
