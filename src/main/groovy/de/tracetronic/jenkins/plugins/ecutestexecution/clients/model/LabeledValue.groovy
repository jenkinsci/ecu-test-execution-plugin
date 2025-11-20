/*
 * Copyright (c) 2024-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

class LabeledValue {
    public String label
    public String value

    /**
     * Abstraction of the ecu.test REST api object LabeledValue in all api versions.
     */
    LabeledValue(String label, String value) {
        this.label = label
        this.value = value
    }

    /**
     * Convert the abstract LabeledValue object to a ecu.test REST api object of the api version V2
     * @return LabeledValue for ecu-test REST api in version V2
     */
    de.tracetronic.cxs.generated.et.client.model.v2.LabeledValue toLabeledValueV2(){
        return new de.tracetronic.cxs.generated.et.client.model.v2.LabeledValue()
                .label(this.label)
                .value(this.value)
    }
}
