/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.cxs.generated.et.client.model.v2.ConfigurationStatus
import de.tracetronic.cxs.generated.et.client.model.v2.Execution
import de.tracetronic.cxs.generated.et.client.model.v2.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.v2.ExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.v2.ModelConfiguration
import de.tracetronic.cxs.generated.et.client.model.v2.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockRestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockApiResponse
import com.google.gson.reflect.TypeToken
import de.tracetronic.cxs.generated.et.client.api.v2.ConfigurationApi
import de.tracetronic.cxs.generated.et.client.api.v2.ExecutionApi
import de.tracetronic.cxs.generated.et.client.model.v2.SimpleMessage
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting
import hudson.Functions
import hudson.model.Result
import okhttp3.Call
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule

class RunPackageStepIT extends IntegrationTestBase {

    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            RunPackageStep before = new RunPackageStep('test.pkg')
        when:
            RunPackageStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip unload test config'() {
        given:
            RunPackageStep before = new RunPackageStep('test.prj')
        and:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('')
            testConfig.setTcfPath('')
            before.setTestConfig(testConfig)
        when:
            RunPackageStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip'() {
        given:
            RunPackageStep before = new RunPackageStep('test.pkg')
        and:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            before.setTestConfig(testConfig)
        and:
            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
            before.setPackageConfig(packageConfig)
        and:
            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            RecordingAsSetting recording = new RecordingAsSetting('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            // recording.setMappingNames(['mapping1', 'mapping2'])
            analysisConfig.setRecordings(Arrays.asList(recording))
            before.setAnalysisConfig(analysisConfig)
        and:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setTimeout(60)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setExecutePackageCheck(true)
            before.setExecutionConfig(executionConfig)
        when:
            RunPackageStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator with Load Configuration'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            RunPackageStep step = new RunPackageStep('test.pkg')
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('')
            testConfig.setTcfPath('')
            step.setTestConfig(testConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage testCasePath: 'test.pkg', " +
                    "testConfig: [tbcPath: '', tcfPath: '']")
        when:
            testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            step.setTestConfig(testConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage testCasePath: 'test.pkg', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
        when:
            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
            step.setPackageConfig(packageConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage packageConfig: [" +
                    "packageParameters: [[label: 'paramLabel', value: 'paramValue']]], testCasePath: 'test.pkg', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
        when:
            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            RecordingAsSetting recording = new RecordingAsSetting('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            analysisConfig.setRecordings(Arrays.asList(recording))
            step.setAnalysisConfig(analysisConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage analysisConfig: [" +
                    "analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "testCasePath: 'test.pkg', testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
        when:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(0)
            executionConfig.setExecutePackageCheck(true)
            step.setExecutionConfig(executionConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage analysisConfig: [" +
                    "analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], " +
                    "executionConfig: [" +
                    "executePackageCheck: true, stopOnError: false, stopUndefinedTools: false, timeout: 0], " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "testCasePath: 'test.pkg', testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
    }

    def 'Snippet generator with Keep Configuration'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            RunPackageStep step = new RunPackageStep('test.pkg')
        then:
            st.assertRoundTrip(step, "ttRunPackage 'test.pkg'")
        when:
            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
            step.setPackageConfig(packageConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage packageConfig: [" +
                    "packageParameters: [[label: 'paramLabel', value: 'paramValue']]], testCasePath: 'test.pkg'")
        when:
            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            RecordingAsSetting recording = new RecordingAsSetting('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            analysisConfig.setRecordings(Arrays.asList(recording))
            step.setAnalysisConfig(analysisConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage analysisConfig: [" +
                    "analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "testCasePath: 'test.pkg'")
        when:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(0)
            executionConfig.setExecutePackageCheck(true)
            step.setExecutionConfig(executionConfig)
        then:
            st.assertRoundTrip(step, "ttRunPackage analysisConfig: [" +
                    "analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], " +
                    "executionConfig: [" +
                    "executePackageCheck: true, stopOnError: false, stopUndefinedTools: false, timeout: 0], " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "testCasePath: 'test.pkg'")
    }

    def 'Run pipeline default'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage 'test.pkg' }", true))
        and:
            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package 'test.pkg'", run)
            jenkins.assertLogNotContains("-> With", run)
    }

    def 'Run pipeline with default TestConfig'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage testCasePath: 'test.pkg', " +
                    "testConfig: [tbcPath: '', tcfPath: ''] }", true))
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package 'test.pkg'...", run)
            jenkins.assertLogContains("-> With TBC=''", run)
            jenkins.assertLogContains("-> With TCF=''", run)
    }

    def 'Run pipeline with TestConfig setup'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage testCasePath: 'test.pkg', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf'] }", true))
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package 'test.pkg'...", run)
            jenkins.assertLogContains("-> With TBC='test.tbc'", run)
            jenkins.assertLogContains("-> With TCF='test.tcf'", run)
            jenkins.assertLogContains("-> With global constants=[[constLabel=constValue]]", run)
            jenkins.assertLogContains("-> With ForceConfigurationReload=true", run)
    }

    def 'Run pipeline with package check'(){
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { " +
                    "ttRunPackage testCasePath:'test.pkg', executionConfig: [executePackageCheck: true]}",
                    true)
            )
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package checks for 'test.pkg'", run)
    }

    def 'Run pipeline: with 409 handling and no config load'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
        and:
            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy() >> MockApiResponse.getResponseUnauthorized()
        and:
            GroovySpy(ExecutionApi, global: true){
                createExecution(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage 'test.pkg' }", true))
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("Executing package 'test.pkg'", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains('unauthorized', run)

    }

    def 'Run pipeline: with 409 handling and config load'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
        and:
            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy() >> MockApiResponse.getResponseUnauthorized()
        and:
            def manageConfigCalled = 0
            GroovySpy(ConfigurationApi, global: true) {
                manageConfiguration(*_) >> {
                    manageConfigCalled =+ 1
                    restApiClient.apiClient.execute(mockCall, new TypeToken<SimpleMessage>(){}.getType())
                }
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage testCasePath: 'test.pkg', " +
                    "testConfig: [tbcPath: '', tcfPath: ''] }", true))
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("Executing package 'test.pkg'", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains('unauthorized', run)
        and:
            // interactive-based testing cannot be used because the method call does not fully end
            assert manageConfigCalled == 1
    }

    def 'Run pipeline: timeout by busy ecu.test'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
        and:
            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy()
        and:
            GroovySpy(ExecutionApi, global: true){
                createExecution(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
                getCurrentExecution() >> new Execution()
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage testCasePath:'test.pkg', " +
                    "executionConfig:[timeout: 2]}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package 'test.pkg'", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains("Execution has exceeded the configured timeout of 2 seconds", run)
    }

    def 'Run pipeline: package path does not exist'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String tempDirString = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage testCasePath: '" +
                    "${tempDirString}/foo/test.pkg' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("ecu.test package at ${tempDirString}/foo/test.pkg does not exist!" +
                    " Please ensure that the path is correctly set and it refers to the desired directory.", run)
    }


    def 'Run pipeline: v2 full config run'() {
            given:
                GroovyMock(RestApiClientFactory, global: true)
                def restApiClient =  new RestApiClientV2('','')
                RestApiClientFactory.getRestApiClient(*_) >> restApiClient
            and:
                def currentExecution = new Execution()
                def status = new ExecutionStatus()
                status.setKey(ExecutionStatus.KeyEnum.FINISHED)
                def result = new ReportInfo()
                result.setResult("result")
                result.setTestReportId("1")
                result.setReportDir("/")
                currentExecution.setStatus(status)
                currentExecution.setResult(result)

                def order = new ExecutionOrder()
                currentExecution.setOrder(order)

                GroovySpy(ExecutionApi, global: true) {
                    createExecution(_) >> new SimpleMessage()
                    getCurrentExecution() >>> [null, currentExecution]
                }
            and:
                def modelConfiguration = new ModelConfiguration()
                def configStatus = new ConfigurationStatus()
                configStatus.setKey(ConfigurationStatus.KeyEnum.FINISHED)
                modelConfiguration.setStatus(configStatus)
                GroovySpy(ConfigurationApi, global: true) {
                    manageConfiguration(_) >> new SimpleMessage()
                    getLastConfigurationOrder() >>>  [null, modelConfiguration]
                }
            and:
                WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
                job.setDefinition(new CpsFlowDefinition("node { ttRunPackage packageConfig: ["+
                                                            "packageParameters: [[label: 'paramLabel', value: 'paramValue']]], testCasePath: 'test.pkg',"+
                                                            "testConfig: [constants: [[label: 'constLabel', value: 'constValue']],"+
                                                            "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf'] }", true))
            when:
                WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            then:
                jenkins.assertLogContains("Executing package 'test.pkg'", run)
                jenkins.assertLogContains("-> With TBC='test.tbc'", run)
                jenkins.assertLogContains("-> With TCF='test.tcf'", run)
                jenkins.assertLogContains("-> With global constants=[[constLabel=constValue]]", run)
                jenkins.assertLogContains("-> With ForceConfigurationReload=true", run)
                jenkins.assertLogContains("-> With package parameters=[[paramLabel=paramValue]]", run)
                jenkins.assertLogContains("Package executed successfully.", run)
                jenkins.assertLogContains("-> reportId: 1",run)
                jenkins.assertLogContains("-> result: result",run)
                jenkins.assertLogContains("-> reportDir: /",run)
        }
}
