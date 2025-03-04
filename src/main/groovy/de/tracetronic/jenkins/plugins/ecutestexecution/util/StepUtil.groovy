/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import org.apache.commons.lang.StringUtils

class StepUtil {

    static List<String> trimAndRemoveEmpty(List<String> entries) {
        entries.findAll(){StringUtils.isNotBlank(it)}.collect{it.trim()}
    }
}
