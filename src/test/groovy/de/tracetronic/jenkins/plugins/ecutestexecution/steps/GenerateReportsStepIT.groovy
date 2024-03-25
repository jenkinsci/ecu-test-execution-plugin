/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.cxs.generated.et.client.api.v2.ReportApi
import de.tracetronic.cxs.generated.et.client.api.v2.StatusApi
import de.tracetronic.cxs.generated.et.client.model.v2.IsIdle
import de.tracetronic.cxs.generated.et.client.model.v2.ReportInfo
import de.tracetronic.cxs.generated.et.client.v2.ApiException
import de.tracetronic.cxs.generated.et.client.v2.ApiResponse
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import hudson.model.Result
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
            st.assertRoundTrip(step, "ttGenerateReports additionalSettings: [" +
                    "[name: 'javascript', value: 'False']], generatorName: 'HTML'")
    }

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttGenerateReports 'HTML' }", true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new TestRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Generating HTML reports...', run)
    }

    def 'Run pipeline: wait until idle ecu.test'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> new RestApiClientV2('','')
            boolean firstCall = true
            GroovySpy(StatusApi, global: true){
                ecutestIsIdle(*_) >> {
                    IsIdle idle = new IsIdle()
                    if (firstCall){
                        idle.setIsIdle(false)
                        return idle
                    }
                    idle.setIsIdle(true)
                    return idle
                }
            }
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttGenerateReports 'HTML' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Generating HTML reports...', run)

    }
}
