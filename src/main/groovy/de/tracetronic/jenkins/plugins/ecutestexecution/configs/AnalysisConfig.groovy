/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs


import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting
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

class AnalysisConfig extends AbstractDescribableImpl<AnalysisConfig> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L

    private String analysisName
    private String mapping
    private List<RecordingAsSetting> recordings

    @DataBoundConstructor
    AnalysisConfig() {
        this.analysisName = ''
        this.mapping = ''
        this.recordings = []
    }

    AnalysisConfig(AnalysisConfig config) {
        this.analysisName = config.getAnalysisName()
        this.mapping = config.getMapping()
        this.recordings = config.getRecordings()
    }

    String getAnalysisName() {
        return analysisName
    }

    @DataBoundSetter
    void setAnalysisName(String analysisName) {
        this.analysisName = StringUtils.trimToEmpty(analysisName)
    }

    String getMapping() {
        return mapping
    }

    @DataBoundSetter
    void setMapping(String mapping) {
        this.mapping = StringUtils.trimToEmpty(mapping)
    }

    List<RecordingAsSetting> getRecordings() {
        return recordings.collect({new RecordingAsSetting(it)})
    }

    @DataBoundSetter
    void setRecordings(List<RecordingAsSetting> recordings) {
        this.recordings = recordings ? removeEmptyRecordings(recordings) : []
    }

    @Override
    String toString() {
        """
        -> analysisName: ${analysisName}
        -> mapping: ${mapping}
        -> recordings: ${recordings.each { it }}
        """.stripIndent().trim()
    }

    @Override
    AnalysisConfig expand(EnvVars envVars) {
        AnalysisConfig expConfig = new AnalysisConfig()
        expConfig.setAnalysisName(envVars.expand(analysisName))
        expConfig.setMapping(envVars.expand(mapping))
        expConfig.setRecordings(recordings.collect({ recording -> recording.expand(envVars) }))
        return expConfig
    }

    private static List<RecordingAsSetting> removeEmptyRecordings(List<RecordingAsSetting> recordings) {
        return recordings.findAll { recording -> StringUtils.isNotBlank(recording.path) }
    }

    @Extension
    static class DescriptorImpl extends Descriptor<AnalysisConfig> {

        @Override
        String getDisplayName() {
            'AnalysisConfig'
        }

        FormValidation doCheckAnalysisName(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }

        FormValidation doCheckMapping(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }
    }
}
