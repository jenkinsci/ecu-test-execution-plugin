/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester

class UploadReportsStepIT extends IntegrationTestBase {

    def setup() {
        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, 'authKey', 'TEST-GUIDE auth key', '', 'authKey'))
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
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Uploading reports to TEST-GUIDE http://localhost:8085...', run)
    }
}
