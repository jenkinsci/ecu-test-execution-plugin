package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import spock.lang.Specification

class RestApiClientFactoryTest extends Specification {

    def "should return RestApiClientV2 when it responds within timeout"() {
        given:
            def mockClientV2 = Mock(RestApiClientV2)
            mockClientV2.waitForAlive(_) >> true

        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(_, _, _) >> mockClientV2

        when:
            def client = RestApiClientFactory.getRestApiClient(_,_,_)

        then:
            client instanceof RestApiClientV2
    }

    def "should return RestApiClientV1 when it responds within timeout"() {
        given:
            def mockClientV1 = Mock(RestApiClientV1)
            mockClientV1.waitForAlive(_) >> true

        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(_, _, _) >> mockClientV1

        when:
            def client = RestApiClientFactory.getRestApiClient(_,_,_)

        then:
            client instanceof RestApiClientV1
    }
}
