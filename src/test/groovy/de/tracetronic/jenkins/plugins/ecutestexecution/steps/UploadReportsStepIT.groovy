/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockRestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockApiResponse
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
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

class UploadReportsStepIT extends IntegrationTestBase {

    def setup() {
        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, 'authKey', 'test.guide auth key', '', 'authKey'))
    }

    def 'Default config round trip'() {
        given:
            UploadReportsStep before = new UploadReportsStep('http://localhost:8085', 'authKey')
        when:
            UploadReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip'() {
        given:
            UploadReportsStep before = new UploadReportsStep('http://localhost:8085', 'authKey')
            before.setProjectId(2)
            before.setUseSettingsFromServer(true)
            before.setAdditionalSettings(Arrays.asList(new AdditionalSetting('uploadToServer', 'True')))
        when:
            UploadReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            UploadReportsStep step = new UploadReportsStep('http://localhost:8085', 'authKey')
        then:
            st.assertRoundTrip(step, "ttUploadReports credentialsId: 'authKey', testGuideUrl: 'http://localhost:8085'")
        when:
            step.setProjectId(2)
        then:
            st.assertRoundTrip(step, "ttUploadReports credentialsId: 'authKey', projectId: 2, " +
                    "testGuideUrl: 'http://localhost:8085'")
        when:
            step.setAdditionalSettings(Arrays.asList(new AdditionalSetting('uploadToServer', 'True')))
        then:
            st.assertRoundTrip(step, "ttUploadReports additionalSettings: [" +
                    "[name: 'uploadToServer', value: 'True']], credentialsId: 'authKey', projectId: 2, " +
                    "testGuideUrl: 'http://localhost:8085'")
    }

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: 'authKey', " +
                            "testGuideUrl: 'http://localhost:8085' }", true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
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
                createUpload(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
                getAllReports(*_) >> {restApiClient.apiClient.execute(mockCall, null)}
            }
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: 'authKey', " +
                            "testGuideUrl: 'http://localhost:8085' }", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains('unauthorized', run)
    }
}
