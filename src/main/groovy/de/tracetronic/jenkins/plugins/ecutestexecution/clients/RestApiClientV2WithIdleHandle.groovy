package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import de.tracetronic.cxs.generated.et.client.v2.ApiClient
import de.tracetronic.cxs.generated.et.client.v2.ApiException
import de.tracetronic.cxs.generated.et.client.v2.ApiResponse
import okhttp3.Call

import java.lang.reflect.Type
import java.util.concurrent.TimeoutException

class RestApiClientV2WithIdleHandle {
    public ApiClient apiClient
    public boolean timeoutExceeded = false

    RestApiClientV2WithIdleHandle(String hostName, String port) {
        apiClient = new ApiClient() {
            /**
             * Execute HTTP call and handle the status 409 (ecu.test is busy) by waiting and trying again.
             * Run until success or timeout handled by RestApiClient and ControllerToAgentCallableWithTimeout
             * {@see de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout}
             * @param returnType : The return type used to deserialize HTTP response body
             * @param call Call
             * @return ApiResponse object containing response status, headers and
             *   data, which is a Java object deserialized from response body and would be null
             *   when returnType is null.
             * @throws ApiException on error status codes (except 409 (busy) where it will wait until success or
             * timeout
             * @throws TimeoutException when the execution time exceeded the timeout
             */
            @Override
            <T> ApiResponse<T> execute(Call call, Type returnType) throws ApiException, TimeoutException {
                while (!timeoutExceeded) {
                    try {
                        return super.execute(call.clone(), returnType)
                    } catch (ApiException e) {
                        if (e.code != 409) {
                            throw e
                        }
                        sleep(1000)
                    }
                }
                throw new TimeoutException("Timeout exceeded during request: ${call.request().method()}: ${call.request().url()}")
            }
        }
        apiClient.setBasePath(String.format('http://%s:%s/api/v2', hostName, port))
    }
}
