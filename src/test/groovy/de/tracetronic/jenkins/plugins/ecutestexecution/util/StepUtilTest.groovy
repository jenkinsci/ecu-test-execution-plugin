/**
 * Copyright: 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import spock.lang.Specification

class StepUtilTest extends Specification{

    def "return empty list for empty reportIds"() {
        expect:
            StepUtil.removeEmptyReportIds([]) == []
    }

    def "return empty list for null input"() {
        expect:
            StepUtil.removeEmptyReportIds(null) == []
    }

    def "remove blank strings and null report ids"() {
        expect:
            StepUtil.removeEmptyReportIds(reportIds) == result

        where:
            reportIds       | result
            ["1", "2", ""]  | ["1", "2"]
            ["1", "2", "3"] | ["1", "2", "3"]
            ["", "2", "3"]  | ["2", "3"]
            [null, "2", "3"]| ["2", "3"]
    }
}
