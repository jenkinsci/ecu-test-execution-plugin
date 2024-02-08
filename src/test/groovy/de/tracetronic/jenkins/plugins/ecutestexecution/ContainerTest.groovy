/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import spock.lang.Specification

class ContainerTest extends Specification {

    protected static final String BASE_IMAGE_PATH =
            "registry.hq-vcs-3.ad.tracetronic.de/hausintern/productdemos/docker/docker-base-images/"
    protected static final String ET_V1_IMAGE_NAME = BASE_IMAGE_PATH + "ecu-test/linux:" + System.getenv('ET_V1_VERSION')
    protected static final String ET_V2_IMAGE_NAME = BASE_IMAGE_PATH + "ecu-test/linux:" + System.getenv('ET_V2_VERSION')
    protected static final int ET_PORT = 5050
    protected static final String ET_WS_PATH = "/app/workspace"
}
