/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import org.apache.commons.lang.StringUtils

class RestApiClientFactory {
    private static int DEFAULT_TIMEOUT = 10
    private static final String DEFAULT_HOSTNAME = 'localhost'
    private static final String DEFAULT_PORT = '5050'
    private static RestApiClient currentClient
    /**
     * Determine the client for the highest REST api version of the given ecu.test.
     * @param hostName (optional) set if ecu.test is hosted on a custom host
     * @param port (optional) set if ecu.test is hosted on a custom port
     * @param timeout (optional) set if a the default timeout of 10 seconds should be override
     * @return RestApiClient object for the highed REST api version
     * @throws ApiException if no REST api client could be determined
     */
    static RestApiClient getRestApiClient(String hostName = DEFAULT_HOSTNAME, String port = DEFAULT_PORT, int timeout = DEFAULT_TIMEOUT) throws ApiException {
        hostName = StringUtils.isBlank(hostName) ? DEFAULT_HOSTNAME : StringUtils.trim(hostName)
        port = StringUtils.isBlank(port) ? DEFAULT_PORT : StringUtils.trim(port)

        currentClient = new RestApiClientV2(hostName, port)
        if (currentClient.waitForAlive(timeout)) {
            return currentClient
        }

        currentClient = new RestApiClientV1(hostName, port)
        if (currentClient.waitForAlive(timeout)){
            return currentClient
        }

        throw new ApiException("Could not find a ecu.test REST api for host: ${hostName}:${port}")
    }

    /**
     * Sets the executionTimedOut of the current ApiClient to true, which will stop the execution of ApiCalls and
     * throw a TimeoutException
     */
    static toggleExecutionTimeout() {
        currentClient.toggleExecutionTimeout()
    }
}
