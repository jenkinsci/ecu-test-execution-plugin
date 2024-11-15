/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting
import hudson.model.TaskListener
import spock.lang.Specification

class LogConfigUtilTest extends Specification {
    private TaskListener listener
    private PrintStream logger
    private String expectedTbcPrint
    private String expectedTcfPrint
    private String expectedConstPrint
    private String expectedParamPrint
    private String expectedEmptyTbcPrint
    private String expectedEmptyTcfPrint
    private String expectedForceReload
    private String expectedAnalysisNamePrint
    private String expectedMappingPrint
    private String expectedRecordingPrint


    def setup () {
        logger = Mock()
        listener = Mock(TaskListener)
        listener.getLogger() >> logger
        expectedTbcPrint = '-> With TBC=\'test.tbc\''
        expectedTcfPrint = '-> With TCF=\'test.tcf\''
        expectedEmptyTbcPrint = '-> With TBC=\'\''
        expectedEmptyTcfPrint = '-> With TCF=\'\''
        expectedForceReload = '-> With ForceConfigurationReload=true'
        expectedConstPrint = '-> With global constants=[[constLabel=constValue]]'
        expectedParamPrint = '-> With package parameters=[[paramLabel=paramValue]]'
        expectedAnalysisNamePrint = '-> With analysis=analysisName'

        expectedMappingPrint = '-> With mapping=mappingName'
        expectedRecordingPrint = '-> With analysis recordings=' +
                '[[-> path: recording.csv\n-> recordingGroup: recordingGroup\n-> ' +
                'mappingNames: []\n-> deviceName: deviceName\n-> formatDetails: formatDetails]]'
    }

    def 'Log Empty Config'() {
        given:
            TestConfig testConfig = new TestConfig()
            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, testConfig)
        when:
            logConfigUtil.log()
        then:
            0* logger.println(expectedForceReload)
            0* logger.println(expectedTbcPrint)
            0* logger.println(expectedTcfPrint)
    }

    def 'Log Test Config'() {
        given:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            TestConfig tcWithLoad = new TestConfig(testConfig)
        and:
            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, tcWithLoad)
        when:
            logConfigUtil.log()
        then:
            1* logger.println(expectedTbcPrint)
            1* logger.println(expectedTcfPrint)
            1* logger.println(expectedConstPrint)
            1* logger.println(expectedForceReload)
    }

    def 'Log Test Config empty config path'() {
        given:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('')
            testConfig.setTcfPath('')
            TestConfig tcWithLoad = new TestConfig(testConfig)
        and:
            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, tcWithLoad)
        when:
            logConfigUtil.log()
        then:
            1* logger.println(expectedEmptyTcfPrint)
            1* logger.println(expectedEmptyTbcPrint)
            0* logger.println(expectedConstPrint)
            0* logger.println(expectedForceReload)
    }

    def 'Log Package Config'() {
        given:
            TestConfig testConfig = new TestConfig()
            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
        and:
            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, testConfig, packageConfig, new AnalysisConfig())
        when:
            logConfigUtil.log()
        then:
            0* logger.println(expectedTbcPrint)
            0* logger.println(expectedTcfPrint)
            0* logger.println(expectedConstPrint)
            0* logger.println(expectedForceReload)
            1* logger.println(expectedParamPrint)
    }

    def 'Log Analysis Config'() {
        given:
            TestConfig testConfig = new TestConfig()
            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            RecordingAsSetting recording = new RecordingAsSetting('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            analysisConfig.setRecordings(Arrays.asList(recording))
        and:
            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, testConfig,
                    new PackageConfig(new ArrayList<PackageParameter>()), analysisConfig)
        when:
            logConfigUtil.log()

        then:
            1* logger.println(expectedAnalysisNamePrint)
            1* logger.println(expectedMappingPrint)
            1* logger.println(expectedRecordingPrint)
    }
}
