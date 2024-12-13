package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification

class StartToolStepTest extends Specification {

    def "setTimeout sets timeout to 0 if negative value is provided"() {
        given:
        def startToolStep = new StartToolStep("ecu.test")
        when:
        startToolStep.setTimeout(-1)

        then:
        startToolStep.getTimeout() == 0
    }

    def "setTimeout sets timeout to the provided value if it is positive"() {
        given:
        def startToolStep = new StartToolStep("ecu.test")

        when:
        startToolStep.setTimeout(100)

        then:
        startToolStep.getTimeout() == 100
    }

    def "setTimeout sets timeout to 0 if value is 0"() {
        given:
        def startToolStep = new StartToolStep("ecu.test")

        when:
        startToolStep.setTimeout(0)

        then:
        startToolStep.getTimeout() == 0
    }
}
