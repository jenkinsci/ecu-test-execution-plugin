/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.cxs.generated.et.client.model.v2.Execution
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
import okhttp3.HttpUrl
import okhttp3.Request
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

    def 'Config round trip'() {
        given:
            RunPackageStep before = new RunPackageStep('test.pkg')

            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            before.setTestConfig(testConfig)

            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
            before.setPackageConfig(packageConfig)

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

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            RunPackageStep step = new RunPackageStep('test.pkg')
        then:
            st.assertRoundTrip(step, "ttRunPackage 'test.pkg'")
        when:
            TestConfig testConfig = new TestConfig()
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
            //recording.setMappingNames(['mapping1', 'mapping2'])
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

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage 'test.pkg' }", true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package 'test.pkg'", run)
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

            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy() >> MockApiResponse.getResponseUnauthorized()

            GroovySpy(ExecutionApi, global: true){
                createExecution(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
            }

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

            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy() >> MockApiResponse.getResponseUnauthorized()

            def manageConfigCalled = 0
            GroovySpy(ConfigurationApi, global: true) {
                manageConfiguration(*_) >> {
                    manageConfigCalled =+ 1
                    restApiClient.apiClient.execute(mockCall, new TypeToken<SimpleMessage>(){}.getType())
                }
            }

            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage testCasePath: 'test.pkg', testConfig: [tbcPath: 'test.tbc'] }", true))
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("Executing package 'test.pkg'", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains('unauthorized', run)
        and:
            // cannot use interactive based testing since the method call was not fully ended
            assert manageConfigCalled == 1
    }

    def 'Run pipeline: timeout by busy ecu.test'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient

            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy()

            GroovySpy(ExecutionApi, global: true){
                createExecution(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
                getCurrentExecution() >> new Execution()
            }

            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage  testCasePath:'test.pkg', executionConfig:[timeout: 2]}", true))
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
            job.setDefinition(new CpsFlowDefinition("node { ttRunPackage testCasePath: '${tempDirString}/foo/test.pkg' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("ecu.test package at ${tempDirString}/foo/test.pkg does not exist!" +
                    " Please ensure that the path is correctly set and it refers to the desired directory.", run)
    }
}
