package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import de.tracetronic.cxs.generated.et.client.api.v2.StatusApi
import de.tracetronic.cxs.generated.et.client.model.v2.SimpleMessage
import spock.lang.Specification

import java.util.concurrent.TimeoutException

class RestApiClientV2Test extends Specification {

    def "waitForAlive should throw TimeoutException when timeout is exceeded"() {
        given:
            def client = new RestApiClientV2("localhost","80")
            client.timeoutExceeded = true

        when:
            client.waitForAlive(1)

        then:
            thrown(TimeoutException)
    }
    def "waitForAlive"() {
        given:
            def client = new RestApiClientV2("localhost","80")
            def statusApi = GroovySpy(StatusApi, global:true)
            statusApi.isAlive() >> new SimpleMessage().message(given)
        when:
            def result = client.waitForAlive(1)
        then:
            result == expected

        where:
            given  | expected
            "Alive"| true
            ""     | false
    }
}
