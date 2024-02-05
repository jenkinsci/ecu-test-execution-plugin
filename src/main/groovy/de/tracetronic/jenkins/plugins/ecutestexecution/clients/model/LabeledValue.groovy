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
     * Convert the abstract LabeledValue object to a ecu.test REST api object of the api version V1
     * @return LabeledValue for ecu-test REST api in version V1
     */
    de.tracetronic.cxs.generated.et.client.model.v1.LabeledValue toLabeledValueV1(){
        return new de.tracetronic.cxs.generated.et.client.model.v1.LabeledValue()
                .label(this.label)
                .value(this.value)
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
