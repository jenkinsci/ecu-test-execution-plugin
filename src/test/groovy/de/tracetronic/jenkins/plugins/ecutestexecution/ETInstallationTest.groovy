package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.Functions
import spock.lang.Specification

class ETInstallationTest extends Specification {

    def "should return correct executable file names based on OS"() {
        when:
            def exeFileNames = ETInstallation.getExeFileNames()

        then:
            if (Functions.isWindows()) {
                exeFileNames == ['ECU-TEST.exe', 'TRACE-CHECK.exe', 'ecu.test.exe', 'trace.check.exe']
            } else {
                exeFileNames == ['ecu-test', 'trace-check', 'ecu.test', 'trace.check']
            }
    }

    def "should throw exception if tool path is invalid or not configured"() {
        given:
            def installation = new ETInstallation("TestTool", "", null)

        when:
            installation.getExeFileOnNode()

        then: "It should throw an IllegalArgumentException"
            def e = thrown(IllegalArgumentException)
            e.message.contains("Tool executable path of 'TestTool' is not configured")
    }

    def "should throw exception if executable is not in the predefined list"() {
        given:
            def installation = new ETInstallation("TestTool", "/valid/path/to/invalidTool", null)

        when:
            installation.getExeFileOnNode()

        then:
            def e = thrown(IllegalArgumentException)
            e.message.contains("Tool executable path of 'TestTool': '/valid/path/to/invalidTool' does not contain a tracetronic tool")
    }
}
