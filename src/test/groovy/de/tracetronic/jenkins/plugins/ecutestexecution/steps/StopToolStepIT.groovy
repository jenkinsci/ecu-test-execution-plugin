/*
 * Copyright (c) 2021-2024 tracetronic GmbH
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
        String executablePath = Functions.isWindows() ? 'C:\\ecutest\\ECU-TEST.exe' : 'bin/ecu-test'
        String executablePathV2 = Functions.isWindows() ? 'C:\\ecutest\\ecu.test.exe' : 'bin/ecu.test'
        etDescriptor.setInstallations(new ETInstallation('ECU-TEST', executablePath, JenkinsRule.NO_PROPERTIES),
                new ETInstallation('ecu.test', executablePathV2, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            StopToolStep before = new StopToolStep(toolName)
        when:
            StopToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
        where:
            toolName = 'ecu.test'
    }

    def 'Config round trip'() {
        given:
            StopToolStep before = new StopToolStep(toolName)
            before.setTimeout(120)
            before.setStopUndefinedTools(false)
        when:
            StopToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
        where:
            toolName = 'ecu.test'
    }

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            StopToolStep step = new StopToolStep(toolName)
        then:
            st.assertRoundTrip(step, "ttStopTool '${toolName}'")
        when:
            step.setTimeout(120)
        then:
            st.assertRoundTrip(step, "ttStopTool timeout: 120, toolName: '${toolName}'")
        when:
            step.setStopUndefinedTools(false)
        then:
            st.assertRoundTrip(step, "ttStopTool stopUndefinedTools: false, timeout: 120, " +
                    "toolName: '${toolName}'")
        where:
            toolName = 'ecu.test'
    }

    @IgnoreIf({ sys["spock.skip.sandbox"] == 'true' })
    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool '${toolName}' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Stopping ${toolName}...", run)
        where:
            toolName = 'ecu.test'
    }

    def 'Run pipeline stop tools'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool '${toolName}' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Stopping ${toolName}...", run)
            jenkins.assertLogContains("${toolName} stopped successfully", run)
            jenkins.assertLogContains('Stop tracetronic tool instances.', run)
            jenkins.assertLogContains('Stopped tracetronic tools successfully.', run)
        where:
            toolName = 'ecu.test'
    }

    def 'Run pipeline tool installation with exception'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool '${toolName}' }", true))
        when:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killProcess(_, _) >> false
        then:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Timeout of 30 seconds exceeded for stopping ${toolName}!", run)
        where:
            toolName = 'ecu.test'
    }

    def 'Run pipeline keep TT tools'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool toolName: '${toolName}', " +
                    "stopUndefinedTools: false }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Stopping ${toolName}...", run)
            jenkins.assertLogContains("${toolName} stopped successfully", run)
            jenkins.assertLogNotContains('Stop tracetronic tool instances.', run)
        where:
            toolName = 'ecu.test'
    }

    def 'Run pipeline stop TT tool with exception'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool 'ecu.test' }", true))
        when:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killProcess(_, _) >> true
            ProcessUtil.killTTProcesses(_) >> false
        then:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Timeout of 30 seconds exceeded for stopping tracetronic tools!', run)
    }
}
