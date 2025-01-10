package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification

class StopToolStepTest extends Specification {

    def "setTimeout sets timeout correctly based on input value"() {
        given:
            def stopToolStep = new StopToolStep("ecu.test")

        when:
            stopToolStep.setTimeout(inputValue)

        then:
            stopToolStep.getTimeout() == expectedTimeout

        where:
            inputValue | expectedTimeout
            -1         | 0
            0          | 0
            100        | 100
    }
}
