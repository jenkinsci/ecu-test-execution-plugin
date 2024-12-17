package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import spock.lang.Specification

class ExecutionConfigTest extends Specification {

//    def "setTimeout sets timeout to 0 if negative value is provided"() {
//        given:
//        def executionConfig = new ExecutionConfig()
//
//        when:
//        executionConfig.setTimeout(-1)
//
//        then:
//        executionConfig.getTimeout() == 0
//    }
//
//    def "setTimeout sets timeout to the provided value if it is positive"() {
//        given:
//        def executionConfig = new ExecutionConfig()
//
//        when:
//        executionConfig.setTimeout(100)
//
//        then:
//        executionConfig.getTimeout() == 100
//    }
//
//    def "setTimeout sets timeout to 0 if value is 0"() {
//        given:
//        def executionConfig = new ExecutionConfig()
//
//        when:
//        executionConfig.setTimeout(0)
//
//        then:
//        executionConfig.getTimeout() == 0
//    }
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
