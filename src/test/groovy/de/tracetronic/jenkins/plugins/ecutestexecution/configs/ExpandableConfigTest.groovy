/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs


import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting
import hudson.EnvVars
import spock.lang.Specification

class ExpandableConfigTest extends Specification {

    def 'Expand analysis config'() {
        given:
            EnvVars envVars = new EnvVars()
            envVars.put('MAPPING_NAME', 'mappingName')
            envVars.put('ANALYSIS_NAME', 'analysisName')
            envVars.put('RECORDING_PATH', 'recording.csv')
            envVars.put('DEVICE_NAME', 'deviceName')
            envVars.put('FORMAT_DETAILS', 'formatDetails')
            envVars.put('RECORDING_GROUP', 'recordingGroup')

            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('${MAPPING_NAME}')
            analysisConfig.setAnalysisName('${ANALYSIS_NAME}')
        RecordingAsSetting recording = new RecordingAsSetting('${RECORDING_PATH}')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            //recording.setMappingNames(['mapping1', 'mapping2'])
            analysisConfig.setRecordings(Arrays.asList(recording))
        when:
            AnalysisConfig expAnalysisConfig = analysisConfig.expand(envVars)
        then:
            expAnalysisConfig.getMapping() == 'mappingName'
            expAnalysisConfig.getAnalysisName() == 'analysisName'
            expAnalysisConfig.getRecordings().get(0).getPath() == 'recording.csv'
            expAnalysisConfig.getRecordings().get(0).getDeviceName() == 'deviceName'
            expAnalysisConfig.getRecordings().get(0).getFormatDetails() == 'formatDetails'
            expAnalysisConfig.getRecordings().get(0).getRecordingGroup() == 'recordingGroup'
    }

    def 'Expand package config'() {
        given:
            EnvVars envVars = new EnvVars()
            envVars.put('PARAM_LABEL', 'paramLabel')
            envVars.put('PARAM_VALUE', 'paramValue')

            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('${PARAM_LABEL}', '${PARAM_VALUE}')))
        when:
            PackageConfig expPackageConfig = packageConfig.expand(envVars)
        then:
            expPackageConfig.getPackageParameters().get(0).getLabel() == 'paramLabel'
            expPackageConfig.getPackageParameters().get(0).getValue() == 'paramValue'
    }

    def 'Expand test config'() {
        given:
            EnvVars envVars = new EnvVars()
            envVars.put('TBC_PATH', 'test.tbc')
            envVars.put('TCF_PATH', 'test.tcf')
            envVars.put('CONSTANT_LABEL', 'constLabel')
            envVars.put('CONSTANT_VALUE', 'constValue')

            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('${TBC_PATH}')
            testConfig.setTcfPath('${TCF_PATH}')
            testConfig.setConstants(Arrays.asList(new Constant('${CONSTANT_LABEL}', '${CONSTANT_VALUE}')))
        when:
            TestConfig expTestConfig = testConfig.expand(envVars)
        then:
            expTestConfig.getTbcPath() == 'test.tbc'
            expTestConfig.getTcfPath() == 'test.tcf'
            expTestConfig.getConstants().get(0).getLabel() == 'constLabel'
            expTestConfig.getConstants().get(0).getValue() == 'constValue'
    }
}