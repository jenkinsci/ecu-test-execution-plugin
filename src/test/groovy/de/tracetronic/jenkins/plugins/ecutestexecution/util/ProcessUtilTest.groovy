package de.tracetronic.jenkins.plugins.ecutestexecution.util

import spock.lang.Specification

class ProcessUtilTest extends Specification {
    def 'test killProcess'(int timeout, expected) {
        given:
            boolean actual = ProcessUtil.killProcess("doesReallyNotExistFoo", timeout)

        expect:
            actual == expected

        where:
            timeout | expected
                -1  |   true
                0   |   true
                1   |   true
    }

    def 'test killProcesses'(int timeout, expected) {
        given:
            boolean actual = ProcessUtil.killProcesses(["doesReallyNotExistFoo", "doesReallyNotExistBar"],
                    timeout)

        expect:
            actual == expected

        where:
            timeout | expected
            -1      |   true
            0       |   true
            1       |   true
    }
}
