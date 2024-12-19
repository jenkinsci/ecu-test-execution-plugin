package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import spock.lang.Specification

class PublishConfigTest extends Specification {

    def "setTimeout sets timeout correctly based on input value"() {
        given:
            def publishConfig = new PublishConfig()

        when:
            publishConfig.setTimeout(inputValue)

        then:
            publishConfig.getTimeout() == expectedTimeout

        where:
            inputValue | expectedTimeout
            -1         | 0
            0          | 0
            100        | 100
    }
}
