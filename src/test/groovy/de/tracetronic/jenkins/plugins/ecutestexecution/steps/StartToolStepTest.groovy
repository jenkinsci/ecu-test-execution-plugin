package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification

class StartToolStepTest extends Specification {

    def "setTimeout sets timeout correctly based on input value"() {
        given:
            def startToolStep = new StartToolStep("ecu.test")

        when:
            startToolStep.setTimeout(inputValue)

        then:
            startToolStep.getTimeout() == expectedTimeout

        where:
            inputValue | expectedTimeout
            -1         | 0
            0          | 0
            100        | 100
    }

}
