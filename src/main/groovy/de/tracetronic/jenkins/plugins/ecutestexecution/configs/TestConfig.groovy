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
import net.sf.json.JSONObject
import org.apache.commons.lang.StringUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter
import org.kohsuke.stapler.StaplerRequest

class TestConfig extends AbstractDescribableImpl<TestConfig> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L

    private boolean loadConfig
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
        this.loadConfig = false
    }

    TestConfig(TestConfig config) {
        this()

        this.loadConfig = config.tcfPath != null || config.tbcPath != null
        if(this.loadConfig) {
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
        this.tbcPath = tbcPath
    }

    @Nullable
    String getTcfPath() {
        return tcfPath
    }

    @DataBoundSetter
    void setTcfPath(String tcfPath) {
        this.tcfPath = tcfPath
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

    boolean getLoadConfig() {
        return loadConfig
    }

    @Override
    String toString() {
        """
        -> tbcPath: ${tbcPath}
        -> tcfPath: ${tcfPath}
        -> forceConfigurationReload: ${forceConfigurationReload}
        -> constants: ${constants.each { it }}
        -> loadConfig: ${loadConfig}
        """.stripIndent().trim()
    }

    @Override
    TestConfig expand(EnvVars envVars) {
        TestConfig expConfig = new TestConfig()
        expConfig.setTbcPath(envVars.expand(tbcPath))
        expConfig.setTcfPath(envVars.expand(tcfPath))
        expConfig.setForceConfigurationReload(forceConfigurationReload)
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

        /**
         * Creates a new instance of {@link TestConfig} from form data.
         * If loadConfig is present and false, removes tbcPath and tcfPath from the form data
         * before creating the instance to ensure they are null in the resulting configuration.
         */
        @Override
        TestConfig newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            def processedFormData = processFormData(formData)
            return (TestConfig) super.newInstance(req, processedFormData);
        }

        FormValidation doCheckTbcPath(@QueryParameter String value) {
            return ValidationUtil.validateFileExtension(value, '.tbc')
        }

        FormValidation doCheckTcfPath(@QueryParameter final String value) {
            return ValidationUtil.validateFileExtension(value, '.tcf')
        }

        protected static JSONObject processFormData(JSONObject formData) {
            if (formData.containsKey("loadConfig") && !formData.getBoolean("loadConfig")) {
                formData.remove("tbcPath")
                formData.remove("tcfPath")
            }
            return formData
        }
    }
}
