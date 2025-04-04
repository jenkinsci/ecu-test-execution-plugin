/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.gson.reflect.TypeToken
import de.tracetronic.cxs.generated.et.client.model.v2.Execution
import de.tracetronic.cxs.generated.et.client.model.v2.SimpleMessage
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockRestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockApiResponse
import de.tracetronic.cxs.generated.et.client.api.v2.ConfigurationApi
import de.tracetronic.cxs.generated.et.client.api.v2.ExecutionApi
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import hudson.Functions
import hudson.model.Result
import okhttp3.Call
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule

class RunProjectStepIT extends IntegrationTestBase {

    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            RunProjectStep before = new RunProjectStep('test.prj')
        when:
            RunProjectStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip unload test config'() {
        given:
            RunProjectStep before = new RunProjectStep('test.prj')
        and:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('')
            testConfig.setTcfPath('')
            before.setTestConfig(testConfig)
        when:
            RunProjectStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip'() {
        given:
            RunProjectStep before = new RunProjectStep('test.prj')
        and:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            before.setTestConfig(testConfig)
        and:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(60)
            executionConfig.setExecutePackageCheck(false)
            before.setExecutionConfig(executionConfig)
        when:
            RunProjectStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator with Load Configuration'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            RunProjectStep step = new RunProjectStep('test.prj')
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('')
            testConfig.setTcfPath('')
            step.setTestConfig(testConfig)
        then:
            st.assertRoundTrip(step, "ttRunProject testCasePath: 'test.prj', " +
                    "testConfig: [tbcPath: '', tcfPath: '']")
        when:
            testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            step.setTestConfig(testConfig)
        then:
            st.assertRoundTrip(step, "ttRunProject testCasePath: 'test.prj', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
        when:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(0)
            executionConfig.setExecutePackageCheck(true)
            step.setExecutionConfig(executionConfig)
        then:
            st.assertRoundTrip(step,
                    "ttRunProject executionConfig: [" +
                    "executePackageCheck: true, stopOnError: false, stopUndefinedTools: false, timeout: 0], " +
                    "testCasePath: 'test.prj', testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
    }

    def 'Snippet generator with Keep Configuration'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            RunProjectStep step = new RunProjectStep('test.prj')
        then:
            st.assertRoundTrip(step, "ttRunProject 'test.prj'")
        when:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(0)
            executionConfig.setExecutePackageCheck(true)
            step.setExecutionConfig(executionConfig)
        then:
            st.assertRoundTrip(step,
                    "ttRunProject executionConfig: [" +
                            "executePackageCheck: true, stopOnError: false, stopUndefinedTools: false, timeout: 0], " +
                            "testCasePath: 'test.prj'")
    }

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunProject 'test.prj' }", true))
        and:
            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
                jenkins.assertLogContains("Executing project 'test.prj'", run)
                jenkins.assertLogNotContains("-> With", run)
    }

    def 'Run pipeline with default TestConfig'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunProject testCasePath: 'test.prj', " +
                    "testConfig: [tbcPath: '', tcfPath: ''] }", true))
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing project 'test.prj'...", run)
            jenkins.assertLogContains("-> With TBC=''", run)
            jenkins.assertLogContains("-> With TCF=''", run)
    }

    def 'Run pipeline with TestConfig setup'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunProject testCasePath: 'test.prj', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "forceConfigurationReload: true, tbcPath: 'test.tbc', tcfPath: 'test.tcf'] }", true))
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing project 'test.prj'...", run)
            jenkins.assertLogContains("-> With TBC='test.tbc'", run)
            jenkins.assertLogContains("-> With TCF='test.tcf'", run)
            jenkins.assertLogContains("-> With global constants=[[constLabel=constValue]]", run)
            jenkins.assertLogContains("-> With ForceConfigurationReload=true", run)
    }

    def 'Run pipeline with package check'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { " +
                    "ttRunProject testCasePath: 'test.prj', executionConfig: [executePackageCheck: true]}",
                    true)
            )
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package checks for 'test.prj'", run)
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
            job.setDefinition(new CpsFlowDefinition("node { ttRunProject 'test.prj' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing project 'test.prj'", run)
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
            job.setDefinition(new CpsFlowDefinition("node { ttRunProject testCasePath: 'test.prj', " +
                    "testConfig: [tbcPath: '', tcfPath: ''] }", true))
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("Executing project 'test.prj'", run)
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
            job.setDefinition(new CpsFlowDefinition("node { ttRunProject testCasePath:'test.prj', " +
                "executionConfig:[timeout: 2]}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing project 'test.prj'", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains("Execution has exceeded the configured timeout of 2 seconds", run)
    }

    def 'Run pipeline: ecu.test folder at path does not exist'() {
        given:
            File tempDir = File.createTempDir()
            String nonExistentFolder = tempDir.getPath().replace('\\', '/') + "/foo"
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunProject testCasePath: '${nonExistentFolder}' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("ecu.test project at ${nonExistentFolder} does not exist! " +
                    "Please ensure that the path is correctly set and it refers to the desired directory.", run)
    }

}
