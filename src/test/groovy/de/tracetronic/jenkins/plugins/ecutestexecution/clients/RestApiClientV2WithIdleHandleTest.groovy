package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import spock.lang.Specification
import de.tracetronic.cxs.generated.et.client.v2.ApiClient
import de.tracetronic.cxs.generated.et.client.v2.ApiException
import okhttp3.Call
import java.util.concurrent.TimeoutException

class RestApiClientV2WithIdleHandleTest extends Specification {

    def "test execution timeout after busy state"() {
        given:
        def client = new RestApiClientV2WithIdleHandle("localhost", "8080")
        def mockCall = Mock(Call)

        and:
        client.metaClass.sleep = { long ms -> }

        and:
        GroovySpy(ApiClient, global: true) {
            execute(_, _) >> { throw new ApiException(409, "Busy") }
        }

        when:
        client.timeoutExceeded = true
        client.apiClient.execute(mockCall, String)

        then:
        thrown(TimeoutException)
    }

    def "test ApiException with error code other than 409"() {
        given:
        def client = new RestApiClientV2WithIdleHandle("localhost", "8080")
        def mockCall = Mock(Call)

        def mockApiClient = Mock(ApiClient)
        client.apiClient = mockApiClient

        mockApiClient.execute(_, _) >> { throw new ApiException(500, "Internal Server Error") }

        when:
        client.timeoutExceeded = false
        client.apiClient.execute(mockCall, String)

        then:
        thrown(ApiException)
    }

    def "test constructor sets correct base path"() {
        when:
        def client = new RestApiClientV2WithIdleHandle("test-host", "1234")

        then:
        client.apiClient.basePath == "http://test-host:1234/api/v2"
    }
}
