/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.gson.reflect.TypeToken
import de.tracetronic.cxs.generated.et.client.api.v2.ChecksApi
import de.tracetronic.cxs.generated.et.client.model.v2.AcceptedCheckExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
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
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockApiResponse

class CheckPackageStepIT extends IntegrationTestBase {

    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            CheckPackageStep before = new CheckPackageStep("test.pkg")
        when:
            CheckPackageStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            CheckPackageStep step = new CheckPackageStep("test.pkg")
        then:
            st.assertRoundTrip(step, "ttCheckPackage 'test.pkg'")
    }

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttCheckPackage testCasePath: 'test.pkg'}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package checks for 'test.pkg'", run)
    }

    def 'Run pipeline: with 409 handling'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient = new RestApiClientV2('', '')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy() >> MockApiResponse.getResponseUnauthorized()
            GroovySpy(ChecksApi, global: true) {
                createCheckExecutionOrder(_) >> { restApiClient.apiClient.execute(mockCall, new TypeToken<AcceptedCheckExecutionOrder>() {}.getType()) }
            }

            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttCheckPackage testCasePath: 'test.pkg'}", true))
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("Executing package checks for 'test.pkg'", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains('unauthorized', run)

    }

    def 'Run pipeline: timeout by busy ecu.test'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient = new RestApiClientV2('', '')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy()

            GroovySpy(ChecksApi, global: true) {
                createCheckExecutionOrder(*_) >> { restApiClient.apiClient.execute(mockCall, null) }
            }
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttCheckPackage testCasePath: 'test.pkg', executionConfig:[timeout: 2]}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing package checks for 'test.pkg'", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains("Execution has exceeded the configured timeout of 2 seconds", run)
    }


}
