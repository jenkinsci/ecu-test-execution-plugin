/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.CredentialsStore
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import de.tracetronic.cxs.generated.et.client.api.v2.ReportApi
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.TGInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockApiResponse
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockRestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestGuideConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult
import hudson.model.Result
import hudson.util.Secret
import okhttp3.Call
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester

class UploadReportsStepIT extends IntegrationTestBase {
    private  CredentialsStore store

    def setup() {
        store = CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, 'authKey', 'test.guide auth key', '', 'authKey'))
        store.addCredentials(Domain.global(), new StringCredentialsImpl(
                CredentialsScope.GLOBAL, 'stringSecret', 'test.guide auth key as secret string',
                Secret.fromString('authKey')))
        store.addCredentials(Domain.global(), new BasicSSHUserPrivateKey(
                    CredentialsScope.GLOBAL, 'unsupportedBaseCredential', 'user',
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource('unsupported'), null,
                    'test.guide auth key as unsupported credentials type'))
        TGInstallation tgInstallation = new TGInstallation('testInstallation', 'http://localhost:8085', 'authKey')
        TestGuideConfig testGuideConfig = jenkins.jenkins.getDescriptorByType(TestGuideConfig.class)
        testGuideConfig.setTgInstallations([tgInstallation])
    }

    def 'Default config round trip'() {
        given:
            UploadReportsStep before = new UploadReportsStep('http://localhost:8085', 'authKey')
        when:
            UploadReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip tg Config'() {
        given:
            UploadReportsStep before = new UploadReportsStep('testInstallation')
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
            UploadReportsStep step = new UploadReportsStep('testInstallation')
        then:
            st.assertRoundTrip(step, "ttUploadReports tgConfiguration: 'testInstallation'")
        when:
            step = new UploadReportsStep('http://localhost:8085', 'authKey')
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
        when:
            step.setFailOnError(false)
        then:
            st.assertRoundTrip(step, "ttUploadReports additionalSettings: [" +
                    "[name: 'uploadToServer', value: 'True']], credentialsId: 'authKey', failOnError: false, " +
                    "projectId: 2, testGuideUrl: 'http://localhost:8085'")
        when:
            step.setReportIds(['1',' 2 ', '', '3'])
        then:
            st.assertRoundTrip(step, "ttUploadReports additionalSettings: [" +
                    "[name: 'uploadToServer', value: 'True']], credentialsId: 'authKey', failOnError: false, " +
                    "projectId: 2, reportIds: ['1', '2', '3'], testGuideUrl: 'http://localhost:8085'")
        when:
            step.setReportIds("2, ,4, 5  ")
        then:
            st.assertRoundTrip(step, "ttUploadReports additionalSettings: [" +
                    "[name: 'uploadToServer', value: 'True']], credentialsId: 'authKey', failOnError: false, " +
                    "projectId: 2, reportIds: ['2', '4', '5'], testGuideUrl: 'http://localhost:8085'")
    }

    def 'Run pipeline default'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: '$credentialsId', " +
                            "testGuideUrl: 'http://localhost:8085' }", true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
        where:
            credentialsId << ['authKey', 'stringSecret']
    }

    def 'Run pipeline default test.guide config'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports tgConfiguration: 'testInstallation' }", true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
    }

    def "Run pipeline unsupported credentials"() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: 'unsupportedBaseCredential', " +
                            "testGuideUrl: 'http://localhost:8085' }", true))
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogNotContains('Uploading reports to test.guide http://localhost:8085...', run)
            jenkins.assertLogContains('No credentials found for authentication key. ' +
                    'Please check the credentials configuration.', run)
    }

    def "Run pipeline invalid test.guide config"() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports tgConfiguration: 'invalidTestInstallation' }", true))
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogNotContains('Uploading reports to test.guide http://localhost:8085...', run)
            jenkins.assertLogContains("Selected test.guide installation 'invalidTestInstallation' not found.", run)
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

    def 'Run pipeline successfully with given report IDs'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClient restApiClientMock =  Mock(RestApiClient)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientMock
            restApiClientMock.uploadReport(*_) >>> [
                    new UploadResult('Success', 'success message', 'link'),
                    new UploadResult('Success', 'another success message', 'link')
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: 'authKey', " +
                            "testGuideUrl: 'http://localhost:8085', reportIds: ['1', '', '3'] }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
            jenkins.assertLogContains('- Uploading ATX report for report id 1...', run)
            jenkins.assertLogContains("-> success message", run)
            jenkins.assertLogContains('- Uploading ATX report for report id 3...', run)
            jenkins.assertLogContains("-> another success message", run)
            jenkins.assertLogContains("Report upload(s) successful", run)
    }

    def 'Run pipeline successfully without given report IDs'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClient restApiClientMock =  Mock(RestApiClient)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientMock
            restApiClientMock.getAllReportIds() >> ['1', '2']
            restApiClientMock.uploadReport(*_) >>> [
                    new UploadResult('Success', 'success message', 'link'),
                    new UploadResult('Success', 'another success message', 'link')
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: 'authKey', " +
                            "testGuideUrl: 'http://localhost:8085' }", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
            jenkins.assertLogContains('- Uploading ATX report for report id 1...', run)
            jenkins.assertLogContains("-> success message", run)
            jenkins.assertLogContains('- Uploading ATX report for report id 2...', run)
            jenkins.assertLogContains("-> another success message", run)
            jenkins.assertLogContains("Report upload(s) successful", run)
    }

    def 'Run pipeline with fail on error'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClient restApiClientMock =  Mock(RestApiClient)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientMock
            restApiClientMock.uploadReport(*_) >>> [
                    new UploadResult('Success', 'success message', 'link'),
                    new UploadResult('Error', 'error message', '')
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: 'authKey', " +
                            "testGuideUrl: 'http://localhost:8085', reportIds: ['1', '', '3'] }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
            jenkins.assertLogContains('- Uploading ATX report for report id 1...', run)
            jenkins.assertLogContains("-> success message", run)
            jenkins.assertLogContains('- Uploading ATX report for report id 3...', run)
            jenkins.assertLogContains("Build result set to ${Result.FAILURE.toString()} due to failed report upload. " +
                    "Set Pipeline step property 'Fail On Error' to 'false' to ignore failed report uploads.", run)
            jenkins.assertLogContains("-> error message", run)
            jenkins.assertLogNotContains("Report upload(s) successful", run)
    }

    def 'Run pipeline with fail on error false'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClient restApiClientMock =  Mock(RestApiClient)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientMock
            restApiClientMock.uploadReport(*_) >>> [
                    new UploadResult('Success', 'success message', 'link'),
                    new UploadResult('Error', 'error message', '')
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttUploadReports credentialsId: 'authKey', " +
                            "testGuideUrl: 'http://localhost:8085', reportIds: ['1', '', '3'], failOnError: false }",
                    true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to test.guide http://localhost:8085...', run)
            jenkins.assertLogContains('- Uploading ATX report for report id 1...', run)
            jenkins.assertLogContains("-> success message", run)
            jenkins.assertLogContains('- Uploading ATX report for report id 3...', run)
            jenkins.assertLogContains("-> error message", run)
            jenkins.assertLogContains("Report upload(s) unstable. Please see the logging of the uploads.", run)
            jenkins.assertLogNotContains("Report upload(s) successful", run)
    }
}
