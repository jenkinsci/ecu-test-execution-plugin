/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule
import hudson.Functions
import spock.lang.IgnoreIf

import java.sql.ResultSet
import java.util.concurrent.TimeoutException

class StopToolStepIT extends IntegrationTestBase {
    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ECU-TEST\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ECU-TEST', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            StopToolStep before = new StopToolStep('ECU-TEST')
        when:
            StopToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip'() {
        given:
            StopToolStep before = new StopToolStep('ECU-TEST')
            before.setTimeout(120)
            before.setStopUndefinedTools(false)
        when:
            StopToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            StopToolStep step = new StopToolStep('ECU-TEST')
        then:
            st.assertRoundTrip(step, "ttStopTool 'ECU-TEST'")
        when:
            step.setTimeout(120)
        then:
            st.assertRoundTrip(step, "ttStopTool timeout: 120, toolName: 'ECU-TEST'")
        when:
            step.setStopUndefinedTools(false)
        then:
            st.assertRoundTrip(step, "ttStopTool stopUndefinedTools: false, timeout: 120, toolName: 'ECU-TEST'")
    }

    @IgnoreIf({ sys["spock.skip.sandbox"] == 'true' })
    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool 'ECU-TEST' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Stopping ECU-TEST...', run)
    }

    def 'Run pipeline stop tools'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool 'ECU-TEST' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Stopping ECU-TEST...', run)
            jenkins.assertLogContains('ECU-TEST stopped successfully', run)
            jenkins.assertLogContains('Stop TraceTronic tool instances.', run)
            jenkins.assertLogContains('Stopped TraceTronic tools successfully.', run)
    }

    def 'Run pipeline tool installation with exception'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool 'ECU-TEST' }", true))
        when:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killProcess(_, _) >> false
        then:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Timeout of 30 seconds exceeded for stopping ECU-TEST!', run)
    }

    def 'Run pipeline keep TT tools'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool toolName: 'ECU-TEST', " +
                    "stopUndefinedTools: false }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Stopping ECU-TEST...', run)
            jenkins.assertLogContains('ECU-TEST stopped successfully', run)
            jenkins.assertLogNotContains('Stop TraceTronic tool instances.', run)
    }

    def 'Run pipeline stop TT tool with exception'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool 'ECU-TEST' }", true))
        when:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killProcess(_, _) >> true
            ProcessUtil.killTTProcesses(_) >> false
        then:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Timeout of 30 seconds exceeded for stopping TraceTronic tools!', run)
    }
}
