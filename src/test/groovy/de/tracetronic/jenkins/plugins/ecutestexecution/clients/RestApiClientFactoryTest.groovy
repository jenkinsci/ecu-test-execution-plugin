/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import spock.lang.Specification

class RestApiClientFactoryTest extends Specification {

    def "should return RestApiClientV2 when it responds within timeout"() {
        given:
            GroovySpy(RestApiClientV2, constructorArgs: ["localhost", "5050"], global: true){
                waitForAlive(_) >> true
            }
            def apiClient = RestApiClientFactory.getRestApiClient()
        expect:
            apiClient instanceof RestApiClientV2
    }
}
