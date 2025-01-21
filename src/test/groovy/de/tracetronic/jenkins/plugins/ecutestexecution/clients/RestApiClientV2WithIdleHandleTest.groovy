package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import spock.lang.Specification
import de.tracetronic.cxs.generated.et.client.v2.ApiClient
import de.tracetronic.cxs.generated.et.client.v2.ApiResponse
import okhttp3.Call

import java.util.concurrent.TimeoutException

class RestApiClientV2WithIdleHandleTest extends Specification {

    def "test constructor sets correct base path"() {
        when:
            def client = new RestApiClientV2WithIdleHandle("test-host", "1234")
        then:
            client.apiClient.basePath == "http://test-host:1234/api/v2"
    }

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
}
