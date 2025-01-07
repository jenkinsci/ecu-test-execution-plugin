/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.cxs.generated.et.client.model.v2.AcceptedCheckExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.v2.CheckExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.v2.CheckFinding
import de.tracetronic.cxs.generated.et.client.model.v2.CheckReport
import de.tracetronic.cxs.generated.et.client.model.v2.ReportGeneration
import de.tracetronic.cxs.generated.et.client.model.v2.ReportGenerationResult
import de.tracetronic.cxs.generated.et.client.model.v2.ReportGenerationStatus
import de.tracetronic.cxs.generated.et.client.model.v2.ReportInfo
import de.tracetronic.cxs.generated.et.client.model.v2.SimpleMessage
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockRestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockApiResponse
import de.tracetronic.cxs.generated.et.client.api.v2.ReportApi
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import hudson.model.Result
import okhttp3.Call
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester

class GenerateReportsStepIT extends IntegrationTestBase {

    def 'Default config round trip'() {
        given:
            GenerateReportsStep before = new GenerateReportsStep('HTML')
        when:
            GenerateReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip'() {
        given:
            GenerateReportsStep before = new GenerateReportsStep('HTML')
            before.setAdditionalSettings(Arrays.asList(new AdditionalSetting('javascript', 'False')))
        when:
            GenerateReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            GenerateReportsStep step = new GenerateReportsStep('HTML')
        then:
            st.assertRoundTrip(step, "ttGenerateReports 'HTML'")
        when:
            step.setAdditionalSettings(Arrays.asList(new AdditionalSetting('javascript', 'False')))
        then:
            st.assertRoundTrip(step, 'ttGenerateReports additionalSettings: [' +
                    "[name: 'javascript', value: 'False']], generatorName: 'HTML'")
    }

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttGenerateReports 'HTML' }", true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Generating HTML reports...', run)
    }

    def 'Run pipeline: with 409 handling'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
            def mockCall = Mock(Call)
            mockCall.clone() >> mockCall
            mockCall.execute() >> MockApiResponse.getResponseBusy() >> MockApiResponse.getResponseUnauthorized()
            GroovySpy(ReportApi, global: true){
                createReportGeneration(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
                getAllReports(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
            }
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttGenerateReports 'HTML' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Generating HTML reports...', run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains('unauthorized', run)
    }

    def 'Run pipeline: v2 without given ids'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
        and:
            def reportInfo = new ReportInfo()
            reportInfo.setTestReportId("1")
            def currentReportGeneration = new ReportGeneration()
            def status = new ReportGenerationStatus()
            def result = new ReportGenerationResult()
            status.setKey(ReportGenerationStatus.KeyEnum.FINISHED)
            status.setMessage("Message")
            result.setOutputDir("/")
            currentReportGeneration.setStatus(status)
            currentReportGeneration.setResult(result)
            GroovySpy(ReportApi, global: true){
                createReportGeneration(*_) >> new SimpleMessage()
                getAllReports(*_) >> [reportInfo]
                getCurrentReportGeneration(_) >>> [null , currentReportGeneration]
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttGenerateReports 'HTML' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Generating HTML reports...', run)
            jenkins.assertLogContains('- Generating HTML report format for report id 1...', run)
            jenkins.assertLogContains('-> FINISHED (Message)', run)
            jenkins.assertLogContains('HTML reports generated successfully', run)
    }
}
