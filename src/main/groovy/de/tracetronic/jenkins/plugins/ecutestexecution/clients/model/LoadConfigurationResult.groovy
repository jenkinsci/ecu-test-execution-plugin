package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

import de.tracetronic.cxs.generated.et.client.model.v2.ConfigurationStatus

class LoadConfigurationResult {

    public final String result;
    public final String message;

    LoadConfigurationResult(String result, String message) {
        this.result = result;
        this.message = message;
    }

    static fromConfigurationStatus(ConfigurationStatus status) {
        return new LoadConfigurationResult(status.key.toString(), status.message)
    }
}
