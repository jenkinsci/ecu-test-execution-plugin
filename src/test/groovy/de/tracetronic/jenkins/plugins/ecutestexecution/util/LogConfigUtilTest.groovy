package de.tracetronic.jenkins.plugins.ecutestexecution.util

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Recording
import hudson.model.TaskListener
import spock.lang.Specification

class LogConfigUtilTest extends Specification {
    private TaskListener listener
    private PrintStream logger
    private String expectedTbcPrint
    private String expectedTcfPrint
    private String expectedConstPrint
    private String expectedParamPrint
    private String expectedAnalysisNamePrint
    private String expectedMappingPrint
    private String expectedRecordingPrint


    def setup () {
        logger = Mock()
        listener = Mock(TaskListener)
        listener.getLogger() >> logger
        expectedTbcPrint = '-> With TBC=test.tbc'
        expectedTcfPrint = '-> With TCF=test.tcf'
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
            0* logger.println(_)

    }

    def 'Log Test Config'() {
        given:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, testConfig)
        when:
            logConfigUtil.log()

        then:
            1* logger.println(expectedTbcPrint)
            1* logger.println(expectedTcfPrint)
            1* logger.println(expectedConstPrint)
            0* logger.println(_)
    }

    def 'Log Package Config'() {
        given:
            TestConfig testConfig = new TestConfig()
            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))

            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, testConfig, packageConfig, new AnalysisConfig())
        when:
            logConfigUtil.log()

        then:
            1* logger.println(expectedParamPrint)
            0* logger.println(_)
    }

    def 'Log Analysis Config'() {
        given:
            TestConfig testConfig = new TestConfig()
            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            Recording recording = new Recording('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            analysisConfig.setRecordings(Arrays.asList(recording))

            LogConfigUtil logConfigUtil = new LogConfigUtil(listener, testConfig,
                    new PackageConfig(new ArrayList<PackageParameter>()), analysisConfig)

        when:
            logConfigUtil.log()

        then:
            1* logger.println(expectedAnalysisNamePrint)
            1* logger.println(expectedMappingPrint)
            1* logger.println(expectedRecordingPrint)
            0* logger.println(_)
    }
}
