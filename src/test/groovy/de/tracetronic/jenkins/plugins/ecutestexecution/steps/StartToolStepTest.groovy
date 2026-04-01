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

    def "sanitizeToolNameForLogFile keeps readable names and neutralizes unsafe parts"() {
        expect:
        StartToolStep.createLogFile("", inputValue, "").getName() == expectedToolName

        where:
        inputValue                  | expectedToolName
        'ecu.test'                  | 'ecu.test'
        'name/with\\separators'     | 'name_with_separators'
        '../..'                     | 'tool'
        '..\\..'                    | 'tool'
        'CON'                       | '_CON'
        '   '                       | 'tool'
        '\u5DE5\u5177 \u540D\u79F0' | '\u5DE5\u5177_\u540D\u79F0'
    }

    def "sanitizeToolNameForLogFile truncates long names to max length"() {
        given:
        String longName = 'A' * (StartToolStep.MAX_WINDOWS_FILE_PATH_LENGTH + 20)

        when:
        File file = StartToolStep.createLogFile("", longName, "")

        then:
        file.absolutePath.length() == StartToolStep.MAX_WINDOWS_FILE_PATH_LENGTH
    }

}
