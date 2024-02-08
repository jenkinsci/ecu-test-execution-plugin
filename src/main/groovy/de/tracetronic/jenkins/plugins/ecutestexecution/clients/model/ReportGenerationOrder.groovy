package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

/**
 * Abstraction of the ecu.test REST api object ReportGenerationOrder in all api versions.
 */
class ReportGenerationOrder {
    public String generatorName
    public Map<String, String> additionalSettings

    ReportGenerationOrder(String generatorName, Map<String, String> additionalSettings) {
        this.generatorName = generatorName
        this.additionalSettings = additionalSettings
    }

    /**
     * Convert the abstract ReportGenerationOrder object to a ecu.test REST api object of the api version V1
     * @return ReportGenerationOrder for ecu-test REST api in version V1
     */
    de.tracetronic.cxs.generated.et.client.model.v1.ReportGenerationOrder toReportGenerationOrderV1(){
        return new de.tracetronic.cxs.generated.et.client.model.v1.ReportGenerationOrder()
                .generatorName(this.generatorName)
                .additionalSettings(this.additionalSettings)
    }

    /**
     * Convert the abstract ReportGenerationOrder object to a ecu.test REST api object of the api version V2
     * @return ReportGenerationOrder for ecu-test REST api in version V2
     */
    de.tracetronic.cxs.generated.et.client.model.v2.ReportGenerationOrder toReportGenerationOrderV2(){
        return new de.tracetronic.cxs.generated.et.client.model.v2.ReportGenerationOrder()
                .generatorName(this.generatorName)
                .additionalSettings(this.additionalSettings)
    }


}
