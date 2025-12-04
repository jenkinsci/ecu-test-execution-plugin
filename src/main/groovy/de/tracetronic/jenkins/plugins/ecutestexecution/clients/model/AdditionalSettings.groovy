/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

/**
 * Abstraction of additional settings for ecu.test REST api. This abstraction will support the different versions of
 * the ecu.test REST api.
 */
class AdditionalSettings {

    private List<Recording> recording
    private String mapping = ''
    private String analysisName = ''
    private Boolean forceConfigurationReload
    private List<LabeledValue> packageParameter

    AdditionalSettings(Boolean forceConfigurationReload) {
        this.forceConfigurationReload = forceConfigurationReload
    }

    AdditionalSettings(String analysisName, List<Recording> recording, String mapping, Boolean forceConfigurationReload, List<LabeledValue> packageParameter) {
        this.analysisName = analysisName
        this.recording = recording
        this.mapping = mapping
        this.forceConfigurationReload = forceConfigurationReload
        this.packageParameter = packageParameter
    }

    /**
     * Convert the abstract AdditionalSettings object to a ecu.test REST api object of the api version V2
     * @return AdditionalSettings for ecu.test REST api in version V2
     */
    de.tracetronic.cxs.generated.et.client.model.v2.AdditionalSettings toAdditionalSettingsV2() {
        List<de.tracetronic.cxs.generated.et.client.model.v2.Recording> recordingsV2 = []
        for (Recording recording : this.recording) {
            recordingsV2.add(recording.toRecordingV2())
        }

        List<de.tracetronic.cxs.generated.et.client.model.v2.LabeledValue> packageParamV2 = []
        for (LabeledValue pkgParam : this.packageParameter) {
            packageParamV2.add(pkgParam.toLabeledValueV2())
        }

        return new de.tracetronic.cxs.generated.et.client.model.v2.AdditionalSettings()
                .analysisName(this.analysisName)
                .recordings(recordingsV2)
                .mapping(this.mapping)
                .packageParameters(packageParamV2)
    }
}
