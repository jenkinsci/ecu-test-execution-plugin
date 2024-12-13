package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification

class StopToolStepTest extends Specification {

    def "setTimeout sets timeout to 0 if negative value is provided"() {
        given:
        def stopToolStep = new StopToolStep("ecu.test")
        when:
        stopToolStep.setTimeout(-1)

        then:
        stopToolStep.getTimeout() == 0
    }

    def "setTimeout sets timeout to the provided value if it is positive"() {
        given:
        def stopToolStep = new StopToolStep("ecu.test")

        when:
        stopToolStep.setTimeout(100)

        then:
        stopToolStep.getTimeout() == 100
    }

    def "setTimeout sets timeout to 0 if value is 0"() {
        given:
        def stopToolStep = new StopToolStep("ecu.test")

        when:
        stopToolStep.setTimeout(0)

        then:
        stopToolStep.getTimeout() == 0
    }
}
