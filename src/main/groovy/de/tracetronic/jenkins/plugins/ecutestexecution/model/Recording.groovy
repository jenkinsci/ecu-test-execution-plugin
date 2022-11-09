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
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

class Recording extends AbstractDescribableImpl<Recording> implements ExpandableConfig, Serializable {

    private static final long serialVersionUID = 1L

    private final String path
    private String recordingGroup
    // TODO: map string list
    private List<String> mappingNames
    private String deviceName
    private String formatDetails

    @DataBoundConstructor
    Recording(String path) {
        this.path = StringUtils.trimToEmpty(path)
        this.recordingGroup = ''
        this.mappingNames = []
        this.deviceName = ''
        this.formatDetails = ''
    }

    String getPath() {
        return path
    }

    String getRecordingGroup() {
        return recordingGroup
    }

    @DataBoundSetter
    void setRecordingGroup(String recordingGroup) {
        this.recordingGroup = StringUtils.trimToEmpty(recordingGroup)
    }

    List<String> getMappingNames() {
        return mappingNames
    }

    @DataBoundSetter
    void setMappingNames(List<String> mappingNames) {
        this.mappingNames = mappingNames ? removeEmptyMappingNames(mappingNames) : []
    }

    String getDeviceName() {
        return deviceName
    }

    @DataBoundSetter
    void setDeviceName(String deviceName) {
        this.deviceName = StringUtils.trimToEmpty(deviceName)
    }

    String getFormatDetails() {
        return formatDetails
    }

    @DataBoundSetter
    void setFormatDetails(String formatDetails) {
        this.formatDetails = StringUtils.trimToEmpty(formatDetails)
    }

    @Override
    String toString() {
        """
        -> path: ${path}
        -> recordingGroup: ${recordingGroup}
        -> mappingNames: ${mappingNames}
        -> deviceName: ${deviceName}
        -> formatDetails: ${formatDetails}
        """.stripIndent().trim()
    }

    @Override
    Recording expand(EnvVars envVars) {
        Recording expRecording = new Recording(envVars.expand(path))
        expRecording.setRecordingGroup(envVars.expand(recordingGroup))
        expRecording.setDeviceName(envVars.expand(deviceName))
        expRecording.setFormatDetails(envVars.expand(formatDetails))
        return expRecording
    }

    private static List<String> removeEmptyMappingNames(List<String> mappingNames) {
        return mappingNames.findAll { mappingName -> StringUtils.isNotBlank(mappingName) }
    }

    @Extension
    static class DescriptorImpl extends Descriptor<Recording> {

        @Override
        String getDisplayName() {
            'Global LabeledValue'
        }

        FormValidation doCheckPath(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value, true)
        }

        FormValidation doCheckRecordingGroup(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }

        FormValidation doCheckDeviceName(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }

        FormValidation doCheckFormatDetails(@QueryParameter String value) {
            return ValidationUtil.validateParameterizedValue(value)
        }
    }
}
