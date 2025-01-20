package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import spock.lang.Specification
import de.tracetronic.cxs.generated.et.client.v2.ApiClient
import de.tracetronic.cxs.generated.et.client.v2.ApiException
import de.tracetronic.cxs.generated.et.client.v2.ApiResponse
import okhttp3.Call

import java.lang.reflect.Type
import java.util.concurrent.TimeoutException

class RestApiClientV2WithIdleHandleTest extends Specification {

    def "test execution timeout after busy state"() {
        given:
            def client = new RestApiClientV2WithIdleHandle("localhost", "8080")
        and:
            client.metaClass.sleep = { long ms -> }
        when:
            client.timeoutExceeded = true
            client.apiClient.execute(Mock(Call), String)
        then:
            thrown(TimeoutException)
    }

    def "test ApiException with error code other than 409"() {
        given:
            def client = new RestApiClientV2WithIdleHandle("localhost", "8080")
            def call = Mock(Call)
            call.clone() >> call
            call.execute() >> { throw new ApiException(500, "Internal Server Error")}
        when:
            client.timeoutExceeded = false
            client.apiClient.execute(call, String)
        then:
            def e = thrown(ApiException)
            e.code == 500
            e.message == "Internal Server Error"
    }

    def "test constructor sets correct base path"() {
        when:
            def client = new RestApiClientV2WithIdleHandle("test-host", "1234")
        then:
            client.apiClient.basePath == "http://test-host:1234/api/v2"
    }


    def "test successful execution after initial busy state"() {
        given:
            def client = new RestApiClientV2WithIdleHandle("localhost", "8080")
            def mockCall = Mock(Call)
            def expectedResponse = new ApiResponse<String>(200, null, "Success")

        and:
            client.metaClass.sleep = { long ms -> }

        and:
            mockCall.clone() >> mockCall

        and:
            def attemptCount = 1
            GroovySpy(ApiClient, global: true) {
                execute(_, _) >> {
                    if (attemptCount == 1) {
                        attemptCount++
                    }
                    return expectedResponse
                }
            }

        when:
            client.timeoutExceeded = false
            def response = client.apiClient.execute(mockCall, String)

        then:
            response == expectedResponse
            attemptCount == 2
    }


    def "test immediate successful execution"() {
        given:
            def client = new RestApiClientV2WithIdleHandle("localhost", "8080")
            def mockCall = Mock(Call)
            def expectedResponse = new ApiResponse<String>(200, null, "Success")

        and:
            mockCall.clone() >> mockCall

        and:
            GroovySpy(ApiClient, global: true) {
                execute(_, _) >> { expectedResponse }
            }

        when:
            def response = client.apiClient.execute(mockCall, String)

        then:
            response == expectedResponse
            noExceptionThrown()
    }
}
