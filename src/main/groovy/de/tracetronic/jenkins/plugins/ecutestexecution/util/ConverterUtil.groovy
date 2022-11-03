package de.tracetronic.jenkins.plugins.ecutestexecution.util

import de.tracetronic.cxs.generated.et.client.model.LabeledValue
import de.tracetronic.cxs.generated.et.client.model.Recording
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting

final class ConverterUtil {

    public static List<LabeledValue> labeledValueConverter(List<Constant> constants) {
        List<LabeledValue> labeledValues = new ArrayList<>()

        constants.each{ constant ->
            LabeledValue labeledValue = new LabeledValue()
            labeledValue.setLabel(constant.getLabel())
            labeledValue.setValue(constant.getValue())
            labeledValues.add(labeledValue)
        }

        return labeledValues
    }

    public static List<Recording> recordingConverter(List<RecordingAsSetting> recordingsAsSetting) {
        List<Recording> recordings = new ArrayList<>()

        recordingsAsSetting.each{ recordingAsSetting ->
            Recording recording = new Recording()
            recording.setPath(recordingAsSetting.getPath())
            recording.setRecordingGroup(recordingAsSetting.getRecordingGroup())
            recording.setMappingNames(recordingAsSetting.getMappingNames())
            recording.setDeviceName(recordingAsSetting.getDeviceName())
            recording.setFormatDetails(recordingAsSetting.getFormatDetails())
            recordings.add(recording)
        }

        return recordings
    }

}
