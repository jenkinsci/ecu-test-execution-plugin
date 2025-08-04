/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import spock.lang.Specification

class ContainerTest extends Specification {
    protected static final String ET_LICENSE_SERVER = System.getenv('tracet_LICENSE')
    protected static final String ET_V1_IMAGE_NAME = System.getenv('DOCKER_REGISTRY_OLD') + "/ecu-test/linux:" + System.getenv('ET_V1_VERSION') // TODO remove once V1 obsolete
    protected static final String ET_V2_IMAGE_NAME = System.getenv('DOCKER_REGISTRY_ARTIFACTORY') + "/tracetronic/ecu.test:" + System.getenv('ET_V2_VERSION')
    protected static final int ET_PORT = 5050
    protected static final String ET_WS_PATH = "/app/workspace"
}
