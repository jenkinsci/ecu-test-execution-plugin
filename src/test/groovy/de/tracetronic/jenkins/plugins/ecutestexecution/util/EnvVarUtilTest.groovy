/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.EnvVars
import spock.lang.Specification

class EnvVarUtilTest extends Specification {

    def "Unsupported class exception"() {
        when:
            new EnvVarUtil()
        then:
            def e = thrown(UnsupportedOperationException)
            e.cause == null
            e.message == "Utility class"

    }

    def "expandVar should return '#expectedResult' for envVar=#envVar and envVars=#vars"() {
        given:
            def envVars = new EnvVars()
            vars.each { k, v -> envVars.put(k, v) }

        when:
            def result = EnvVarUtil.expandVar(envVar, envVars, "default")

        then:
            result == expectedResult

        where:
            envVar              | vars                              | expectedResult
            'TEST_VAR'          | [TEST_VAR: "value"]               | "TEST_VAR"
            'MISSING_VAR'       | [TEST_VAR: "value"]               | "MISSING_VAR"
            '${TEST_VAR}'       | [TEST_VAR: "value"]               | "value"
            '${VAR1} ${VAR2}'   | [VAR1: "Hello", VAR2: "World"]    | "Hello World"
    }

    def "expandVar should return default value"() {
        given:
            def envVars = new EnvVars()

        when:
            def result = EnvVarUtil.expandVar(envVar, envVars, "default")

        then:
            result == "default"

        where:
            envVar << [null, "", "   "]
    }
}
