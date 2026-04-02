package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

class StartToolStepTest extends Specification {

    @Unroll
    def "setTimeout sets timeout correctly based on input value"() {
        given:
        def startToolStep = new StartToolStep("ecu.test")

        when:
        startToolStep.setTimeout(inputValue)
        int actualTimeout = startToolStep.getTimeout()

        then:
        assert actualTimeout == expectedTimeout:
            "Unexpected timeout for input '#{inputValue}'. Expected #{expectedTimeout}, but was #{actualTimeout}."

        where:
        inputValue | expectedTimeout
        -1         | 0
        0          | 0
        100        | 100
    }

    @Unroll
    def "sanitizeToolNameForLogFile keeps readable names and neutralizes unsafe parts"() {
        when:
        File file = StartToolStep.createLogFile("", inputValue, "")
        String actualToolName = file.getName()

        then:
        assert actualToolName == expectedToolName:
            "Unexpected sanitized tool name for input '#{inputValue}'. Expected '#{expectedToolName}', but was '#{actualToolName}' (absolutePath='#{file.absolutePath}')."

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

    @IgnoreIf({ !os.isWindows() })
    def "sanitizeToolNameForLogFile truncates long names to max length"() {
        given:
        String longName = 'A' * (StartToolStep.MAX_WINDOWS_FILE_PATH_LENGTH + 20)

        when:
        File file = StartToolStep.createLogFile("", longName, "")
        int actualPathLength = file.absolutePath.length()

        then:
        assert actualPathLength == StartToolStep.MAX_WINDOWS_FILE_PATH_LENGTH:
            "Unexpected log file path length. Expected #{StartToolStep.MAX_WINDOWS_FILE_PATH_LENGTH}, but was #{actualPathLength} (absolutePath='#{file.absolutePath}')."
    }

}
