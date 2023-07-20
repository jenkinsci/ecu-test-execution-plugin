package de.tracetronic.jenkins.plugins.ecutestexecution.util

import spock.lang.IgnoreIf
import spock.lang.Specification

class ProcessUtilTest extends Specification {
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
    def 'test killProcesses'(int timeout, expected) {
        expect:
            ProcessUtil.killProcesses(["doesReallyNotExistFoo", "doesReallyNotExistBar"], timeout) == expected

        where:
            timeout | expected
            -1      |   false
            0       |   false
            1       |   true
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
