/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.EnvVars
import org.apache.commons.lang.StringUtils

final class EnvVarUtil {

    private EnvVarUtil() {
        throw new UnsupportedOperationException('Utility class')
    }

    /**
     * Expands a variable using build environment variables or returns the default value.
     *
     * @param envVar the environment variable to expand
     * @param envVars the existing build environment variable
     * @param defaultValue the default value if environment variable is empty
     * @return the expanded environment variable or default value
     */
    static String expandVar(String envVar, EnvVars envVars, String defaultValue) {
        return StringUtils.isBlank(envVar) ? defaultValue : envVars.expand(envVar)
    }
}
