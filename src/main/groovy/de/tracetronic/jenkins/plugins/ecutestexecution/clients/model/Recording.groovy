/*
 * Copyright (c) 2024-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

/**
 * Abstraction of the ecu.test REST api object Recording in all api versions.
 */
class Recording {

    String path
    String recordingGroup
    List<String> mappingNames
    String deviceName
    String formatDetails

    Recording(String path, String recordingGroup, List<String> mappingNames, String deviceName, String formatDetails){
        this.path = path
        this.recordingGroup = recordingGroup
        this.mappingNames = mappingNames
        this.deviceName = deviceName
        this.formatDetails = formatDetails
    }

    /**
     * Convert the abstract Recording object to a ecu.test REST api object of the api version V2
     * @return Recording for ecu-test REST api in version V2
     */
    de.tracetronic.cxs.generated.et.client.model.v2.Recording toRecordingV2(){
        return new de.tracetronic.cxs.generated.et.client.model.v2.Recording()
                .path(this.path)
                .recordingGroup(this.recordingGroup)
                .mappingNames(this.mappingNames)
                .deviceName(this.deviceName)
                .formatDetails(this.formatDetails)
    }
}
