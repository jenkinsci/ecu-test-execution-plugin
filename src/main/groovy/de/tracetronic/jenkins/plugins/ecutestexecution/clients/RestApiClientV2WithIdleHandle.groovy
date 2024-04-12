package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import de.tracetronic.cxs.generated.et.client.v2.ApiClient
import de.tracetronic.cxs.generated.et.client.v2.ApiException
import de.tracetronic.cxs.generated.et.client.v2.ApiResponse
import okhttp3.Call

import java.lang.reflect.Type

class RestApiClientV2WithIdleHandle {
    public ApiClient apiClient

    RestApiClientV2WithIdleHandle(String hostName, String port) {
        apiClient = new ApiClient() {
            @Override
            <T> ApiResponse<T> execute(Call call, Type returnType) throws ApiException {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        return super.execute(call.clone(), returnType)
                    } catch (ApiException e) {
                        if (e.code != 409) {
                            throw e
                        }
                        sleep(1000)
                    }
                }
            }
        }
        apiClient.setBasePath(String.format('http://%s:%s/api/v2', hostName, port))
    }
}
