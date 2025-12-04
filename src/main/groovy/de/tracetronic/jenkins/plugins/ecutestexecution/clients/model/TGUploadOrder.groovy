/*
 * Copyright (c) 2024-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

/**
 * Abstraction of the ecu.test REST api object TGUploadOrder in all api versions.
 */
class TGUploadOrder {
    private String testGuideUrl;
    private String authKey = "";
    private Integer projectId = 0;
    private Boolean useSettingsFromServer = false;
    private Map<String, String> additionalSettings = new HashMap<>();

    TGUploadOrder(String testGuideUrl, String authKey, Integer projectId, Boolean useSettingsFromServer, Map<String, String> additionalSettings) {
        this.testGuideUrl = testGuideUrl
        this.authKey = authKey
        this.projectId = projectId
        this.useSettingsFromServer = useSettingsFromServer
        this.additionalSettings = additionalSettings
    }

    /**
     * Convert the abstract TGUploadOrder object to a ecu.test REST api object of the api version V2
     * @return TGUploadOrder for ecu-test REST api in version V2
     */
    de.tracetronic.cxs.generated.et.client.model.v2.TGUploadOrder toTGUploadOrderV2() {
        return new de.tracetronic.cxs.generated.et.client.model.v2.TGUploadOrder()
                .testGuideUrl(this.testGuideUrl)
                .authKey(this.authKey)
                .projectId(this.projectId)
                .useSettingsFromServer(this.useSettingsFromServer)
                .additionalSettings(this.additionalSettings)
    }
}
