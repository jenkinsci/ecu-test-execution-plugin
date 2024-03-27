/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.cxs.generated.et.client.api.v2.ChecksApi
import de.tracetronic.cxs.generated.et.client.api.v2.StatusApi
import de.tracetronic.cxs.generated.et.client.model.v2.IsIdle
import de.tracetronic.cxs.generated.et.client.v2.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import hudson.Functions
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule

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
            jenkins.assertLogContains("Executing Package Checks for: test.pkg", run)
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
                        firstCall = false
                        idle.setIsIdle(false)
                        return idle
                    }
                    idle.setIsIdle(true)
                    return idle
                }
            }
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttCheckPackage testCasePath: 'test.pkg'}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing Package Checks for: test.pkg", run)
    }

    def 'Run pipeline: timeout by busy ecu.test'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> new RestApiClientV2('','')
            GroovySpy(ChecksApi, global: true){
                createCheckExecutionOrder(*_) >> { throw new ApiException(409, 'ecu.test is busy')}
            }
            GroovySpy(StatusApi, global: true) {
                ecutestIsIdle(*_) >> {
                    IsIdle idle = new IsIdle()
                    idle.setIsIdle(false)
                    return idle
                }
            }
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttCheckPackage testCasePath: 'test.pkg', executionConfig:[timeout: 2]}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Executing Package Checks for: test.pkg", run)
            jenkins.assertLogNotContains('ecu.test is busy', run)
            jenkins.assertLogContains("Timeout: check package test.pkg waited 2 seconds for ecu.test to become idle", run)
    }

}
