package de.tracetronic.jenkins.plugins.ecutestexecution.util

import spock.lang.Specification

class ProcessUtilTest extends Specification {
    def 'test killProcess'(int timeout, expected) {
        expect:
            ProcessUtil.killProcess("doesReallyNotExistFoo", timeout) == expected

        where:
            timeout | expected
                -1  |   true
                0   |   true
                1   |   true
    }

    def 'test killProcesses'(int timeout, expected) {
        expect:
            ProcessUtil.killProcesses(["doesReallyNotExistFoo", "doesReallyNotExistBar"], timeout) == expected

        where:
            timeout | expected
            -1      |   true
            0       |   true
            1       |   true
    }
}
