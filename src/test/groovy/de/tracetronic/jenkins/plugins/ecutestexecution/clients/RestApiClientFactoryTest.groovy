package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import spock.lang.Ignore
import spock.lang.Specification

class RestApiClientFactoryTest extends Specification {
    //TODO metaClass.constructor mock seems to interfere with other tests

    @Ignore
    def "should return RestApiClientV2 when it responds within timeout"() {
        given:
            def mockClientV2 = Mock(RestApiClientV2)
            RestApiClientV2.metaClass.constructor = { String host, String port ->
                mockClientV2
            }
            mockClientV2.waitForAlive(_) >> true
            def apiClient = RestApiClientFactory.getRestApiClient()
        expect:
            apiClient instanceof RestApiClientV2
    }

    @Ignore
    def "should return RestApiClientV1 when it responds within timeout and RestApiClientV2 not alive"() {
        given:
            def mockClientV1 = Mock(RestApiClientV1)
            RestApiClientV1.metaClass.constructor = { String host, String port ->
                mockClientV1
            }
            mockClientV1.waitForAlive(_) >> true
            def apiClient = RestApiClientFactory.getRestApiClient()
        expect:
            apiClient instanceof RestApiClientV1
    }
}
