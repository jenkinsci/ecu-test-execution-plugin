/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import spock.lang.Specification

class ContainerTest extends Specification {
    protected static final String ET_LICENSE_SERVER = System.getenv('ET_LICENSE_SERVER')
    protected static final String ET_V2_IMAGE_NAME = System.getenv('REGISTRY_ARTIFACTORY') + "/tracetronic/ecu.test:" + System.getenv('ET_V2_VERSION')
    protected static final int ET_PORT = 5050
    protected static final String ET_WS_PATH = "/app/workspace"
}
