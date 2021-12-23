/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import hudson.EnvVars

/**
 * Interface describing a factory to expand specific configurations.
 */
interface ExpandableConfig {

    /**
     * Expands the configuration parameters by using the current build environment variables.
     *
     * @param envVars the build environment variables
     * @return the expanded configuration
     */
    ExpandableConfig expand(EnvVars envVars)
}
