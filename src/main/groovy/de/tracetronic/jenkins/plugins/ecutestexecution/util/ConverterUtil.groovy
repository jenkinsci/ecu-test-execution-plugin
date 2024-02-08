/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.LabeledValue
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.Recording
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting

import java.awt.Label

final class ConverterUtil {

    static List<Recording> recordingConverter(List<RecordingAsSetting> recordingsAsSetting) {
        List<Recording> recordings = new ArrayList<>()

        recordingsAsSetting.each{ recordingAsSetting ->
            Recording recording = new Recording(recordingAsSetting.getPath(), recordingAsSetting.getRecordingGroup(),
                    recordingAsSetting.getMappingNames(), recordingAsSetting.getDeviceName(),
                    recordingAsSetting.getFormatDetails())
            recordings.add(recording)
        }

        return recordings
    }

    static List<LabeledValue> labeledValueConverter(List<PackageParameter> pkgParams) {
        List<LabeledValue> labeledValues = new ArrayList<>()

        pkgParams.each{ pkgParam ->
            labeledValues.add(new LabeledValue(pkgParam.label, pkgParam.value))
        }

        return labeledValues
    }

}
