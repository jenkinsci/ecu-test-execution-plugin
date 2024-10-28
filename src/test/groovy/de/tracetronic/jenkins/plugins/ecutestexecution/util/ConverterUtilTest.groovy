/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util


import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting
import spock.lang.Specification

class ConverterUtilTest extends Specification {

    def "Unsupported class exception"() {
        expect:
            ConverterUtil.recordingConverter(new ArrayList<RecordingAsSetting>())
                    .isEmpty()
    }

    def "should convert single RecordingAsSetting to Recording"() {
        given:
            def recordingAsSetting = new RecordingAsSetting("/test/path")
            recordingAsSetting.setRecordingGroup("testGroup")
            recordingAsSetting.setMappingNames(["mapping1", "mapping2"])
            recordingAsSetting.setDeviceName("testDevice")
            recordingAsSetting.setFormatDetails("testFormat")

        when:
            def result = ConverterUtil.recordingConverter([recordingAsSetting])

        then:
            result.size() == 1
            with(result[0]) {
                path == "/test/path"
                recordingGroup == "testGroup"
                mappingNames == ["mapping1", "mapping2"]
                deviceName == "testDevice"
                formatDetails == "testFormat"
        }
    }

    def "should convert multiple RecordingAsSettings to Recordings"() {
        given:
            def recordingAsSetting1 = new RecordingAsSetting("/test/path")
            recordingAsSetting1.setRecordingGroup("testGroup")
            recordingAsSetting1.setMappingNames(["mapping1", "mapping2"])
            recordingAsSetting1.setDeviceName("testDevice")
            recordingAsSetting1.setFormatDetails("testFormat")

            def recordingAsSetting2 = new RecordingAsSetting("/test/path2")
            recordingAsSetting2.setRecordingGroup("testGroup2")
            recordingAsSetting2.setMappingNames(["mapping3", "mapping4"])
            recordingAsSetting2.setDeviceName("testDevice2")
            recordingAsSetting2.setFormatDetails("testFormat2")

        when:
            def result = ConverterUtil.recordingConverter([recordingAsSetting1, recordingAsSetting2])

        then:
            result.size() == 2
            with(result[0]) {
                path == "/test/path"
                recordingGroup == "testGroup"
                mappingNames == ["mapping1", "mapping2"]
                deviceName == "testDevice"
                formatDetails == "testFormat"
            }
            with(result[1]) {
                path == "/test/path2"
                recordingGroup == "testGroup2"
                mappingNames == ["mapping3", "mapping4"]
                deviceName == "testDevice2"
                formatDetails == "testFormat2"
            }
    }

    def "should convert empty list of PackageParameter to empty list of LabeledValue"() {
        expect:
            ConverterUtil.labeledValueConverter(new ArrayList<PackageParameter>()).isEmpty()
    }

    def "should convert PackageParameter to LabeledValue"() {
        given:
            def pkgParam = new PackageParameter('label', 'value')

        when:
            def result = ConverterUtil.labeledValueConverter([pkgParam])

        then:
            result.size() == 1
            with(result[0]) {
                it.label == "label"
                it.value == "value"
            }
    }

    def "should convert multiple PackageParameter to LabeledValues"() {
        given:
            def pkgParam1 = new PackageParameter('label', 'value')
            def pkgParam2 = new PackageParameter('label2', 'value2')

        when:
        def result = ConverterUtil.labeledValueConverter([pkgParam1, pkgParam2])

        then:
            result.size() == 2
            with(result[0]) {
                it.label == "label"
                it.value == "value"
            }
            with(result[1]) {
                it.label == "label2"
                it.value == "value2"
            }
    }
}
