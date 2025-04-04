package de.tracetronic.jenkins.plugins.ecutestexecution.model

import hudson.EnvVars
import hudson.util.FormValidation
import spock.lang.Specification

class RecordingAsSettingTest extends Specification {

    def "RecordingAsSetting default initialization"() {
        when:
            def setting = new RecordingAsSetting("")

        then:
            setting.path == ""
            setting.recordingGroup == ""
            setting.deviceName == ""
            setting.formatDetails == ""
            setting.mappingNames.isEmpty()
    }

    def "RecordingAsSetting initialization with values"() {
        when:
            def setting = new RecordingAsSetting("path/to/recording")

        then:
            setting.path == "path/to/recording"
    }

    def "setRecordingGroup trims input values"() {
        given:
            def setting = new RecordingAsSetting( "")

        when:
            setting.recordingGroup = "   group   "

        then:
            setting.recordingGroup == "group"
    }

    def "setMappingNames removes empty values and handles empty list"() {
        given:
            def setting = new RecordingAsSetting("")

        when:
            setting.mappingNames = given

        then:
            setting.mappingNames == expected

        where:
            given                           | expected
             ["name1", "", "   ", "name2"]  | ["name1", "name2"]
             []                             | []
             null                           | []
    }

    def "expand expands all fields"() {
        given:
            def setting = new RecordingAsSetting("\${PATH}")
            setting.recordingGroup = "\${GROUP}"
            setting.deviceName = "\${DEVICE}"
            setting.formatDetails = "\${DETAILS}"
            def envVars = new EnvVars([
                    PATH    : "expanded/path",
                    GROUP   : "expandedGroup",
                    DEVICE  : "expandedDevice",
                    DETAILS : "expandedDetails"
            ])

        when:
            def expandedSetting = setting.expand(envVars)

        then:
            expandedSetting.path == "expanded/path"
            expandedSetting.recordingGroup == "expandedGroup"
            expandedSetting.deviceName == "expandedDevice"
            expandedSetting.formatDetails == "expandedDetails"
    }

    def "Validation checks for path"() {
        given:
            def descriptor = new RecordingAsSetting.DescriptorImpl()

        expect:
            descriptor.doCheckPath(path).kind == expectedKind

        where:
            path                | expectedKind
            "valid/path"        | FormValidation.Kind.OK
            null                | FormValidation.Kind.ERROR
            ""                  | FormValidation.Kind.ERROR
    }

    def "toString returns formatted string representation"() {
        given:
            def setting = new RecordingAsSetting("path/to/recording")
            setting.recordingGroup = "group"
            setting.mappingNames = ["name1", "name2"]
            setting.deviceName = "device"
            setting.formatDetails = "details"

        expect:
            setting.toString() == """
                -> path: path/to/recording
                -> recordingGroup: group
                -> mappingNames: [name1, name2]
                -> deviceName: device
                -> formatDetails: details
                """.stripIndent().trim()
    }
}
