/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.Functions
import hudson.Proc
import spock.lang.IgnoreIf
import spock.lang.Specification

class ProcessUtilTest extends Specification {

    def "Unsupported class exception"() {
        when:
            new ProcessUtil()
        then:
            def e = thrown(UnsupportedOperationException)
            e.cause == null
            e.message == "Utility class"
    }

    @IgnoreIf({ sys["spock.skip.sandbox"] == 'true' })
    def 'test killProcess'(int timeout, expected) {
        expect:
            ProcessUtil.killProcess("doesReallyNotExistFoo", timeout) == expected

        where:
            timeout | expected
                -1  |   false
                0   |   false
                1   |   true
    }

    @IgnoreIf({ sys["spock.skip.sandbox"] == 'true' })
    def 'test killProcess for different os'() {
        given:
            GroovyMock(Functions, global: true)
            Functions.isWindows() >> isWindows
        and:
            def mockProcess = GroovyMock(Process)
            def mockBuilder = GroovyMock(ProcessBuilder)
            GroovyMock(ProcessBuilder, global: true)
            new ProcessBuilder() >> mockBuilder
            def withArgs
            mockBuilder.command(_) >> { args ->
                withArgs = args[0]
                mockBuilder
            }
            mockBuilder.start() >> mockProcess
            mockProcess.waitFor() >> 0
        when:
            def result = ProcessUtil.killProcess("doesReallyNotExistFoo", 0)
        then:
            result
            withArgs.contains(processName)
        where:
            isWindows << [false, true]
            processName << ['pkill', 'taskkill.exe']
    }

    @IgnoreIf({ sys["spock.skip.sandbox"] == 'true' })
    def 'test killProcesses'() {
        expect:
            ProcessUtil.killProcesses(processes, timeout) == expected

        where:
            processes                                           | timeout   | expected
            ["doesReallyNotExistFoo", "doesReallyNotExistBar"]  | -1        |   false
            ["doesReallyNotExistFoo"]                           | 0         |   false
            []                                                  | 1         |   true
            null                                                | 1         |   true
    }

    def 'test killTTProcesses'(int timeout, expected) {
        expect:
            ProcessUtil.killTTProcesses(timeout) == expected

        where:
            timeout | expected
            -1      |   false
            0       |   false
            1       |   true
    }

    def 'test killTTProcesses default'() {
        expect:
            ProcessUtil.killTTProcesses() == true

    }
}
