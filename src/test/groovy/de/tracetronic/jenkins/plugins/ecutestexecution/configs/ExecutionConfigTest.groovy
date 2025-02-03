package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import spock.lang.Specification

class ExecutionConfigTest extends Specification {

    def "setTimeout sets timeout correctly based on input value"() {
        given:
            def executionConfig = new ExecutionConfig()

        when:
            executionConfig.setTimeout(inputValue)

        then:
            executionConfig.getTimeout() == expectedTimeout

        where:
            inputValue | expectedTimeout
            -1         | 0
            0          | 0
            100        | 100
    }
}
