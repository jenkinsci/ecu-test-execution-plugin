package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import spock.lang.Specification

class PublishConfigTest extends Specification {

    def "setTimeout sets timeout to 0 if negative value is provided"() {
        given:
        def publishConfig = new PublishConfig()

        when:
        publishConfig.setTimeout(-1)

        then:
        publishConfig.getTimeout() == 0
    }

    def "setTimeout sets timeout to the provided value if it is positive"() {
        given:
        def publishConfig = new PublishConfig()

        when:
        publishConfig.setTimeout(100)

        then:
        publishConfig.getTimeout() == 100
    }

    def "setTimeout sets timeout to 0 if value is 0"() {
        given:
        def publishConfig = new PublishConfig()

        when:
        publishConfig.setTimeout(0)

        then:
        publishConfig.getTimeout() == 0
    }
}
