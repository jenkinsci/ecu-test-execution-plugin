/**
 * Copyright: 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import spock.lang.Specification

class StepUtilTest extends Specification{

    def "return empty list for empty input"() {
        expect:
            StepUtil.trimAndRemoveEmpty([]) == []
    }

    def "return empty list for null input"() {
        expect:
            StepUtil.trimAndRemoveEmpty(null) == []
    }

    def "remove whitespace, blank strings and null entries"() {
        expect:
            StepUtil.trimAndRemoveEmpty(reportIds) == result

        where:
            reportIds       | result
            ["1", " 2 ", ""]  | ["1", "2"]
            ["1", " 2 ", "3"] | ["1", "2", "3"]
            ["", " 2 ", "3"]  | ["2", "3"]
            [null, " 2 ", "3"]| ["2", "3"]
    }
}
