/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import hudson.Functions
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule

import java.nio.file.Files
import java.nio.file.Paths

class StartToolStepIT extends IntegrationTestBase {

    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ECU-TEST\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ECU-TEST', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            StartToolStep before = new StartToolStep('ECU-TEST')
        when:
            StartToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip'() {
        given:
            StartToolStep before = new StartToolStep('ECU-TEST')
            before.setWorkspaceDir('workspace')
            before.setSettingsDir('settings')
            before.setTimeout(120)
            before.setKeepInstance(true)
            before.setStopUndefinedTools(false)
        when:
            StartToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            StartToolStep step = new StartToolStep('ECU-TEST')
        then:
            st.assertRoundTrip(step, "ttStartTool 'ECU-TEST'")
        when:
            step.setWorkspaceDir('workspace')
        then:
            st.assertRoundTrip(step, "ttStartTool toolName: 'ECU-TEST', workspaceDir: 'workspace'")
        when:
            step.setSettingsDir('settings')
        then:
            st.assertRoundTrip(step, "ttStartTool settingsDir: 'settings', toolName: 'ECU-TEST', " +
                    "workspaceDir: 'workspace'")
        when:
            step.setTimeout(120)
        then:
            st.assertRoundTrip(step, "ttStartTool settingsDir: 'settings', timeout: 120, toolName: 'ECU-TEST', " +
                    "workspaceDir: 'workspace'")
        when:
            step.setKeepInstance(true)
        then:
            st.assertRoundTrip(step, "ttStartTool keepInstance: true, settingsDir: 'settings', timeout: 120, " +
                    "toolName: 'ECU-TEST', workspaceDir: 'workspace'")
        when:
            step.setStopUndefinedTools(false)
        then:
            st.assertRoundTrip(step, "ttStartTool keepInstance: true, settingsDir: 'settings', " +
                    "stopUndefinedTools: false, timeout: 120, toolName: 'ECU-TEST', workspaceDir: 'workspace'")
    }

    def 'Run pipeline: Settings dir does not exist'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String tempDirString = tempDir.getPath().replace('\\', '/')

            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')

            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ECU-TEST', " +
                    "workspaceDir: '${tempDirString}', settingsDir: '${tempDirString}/foo' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("ECU-TEST settings directory created at ${tempDirString}/foo", run)
            jenkins.assertLogContains('Starting ECU-TEST...', run)
            Files.exists(Paths.get("${tempDirString}/foo"))
    }

    def 'Run pipeline'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ECU-TEST', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Stop TraceTronic tool instances.', run)
            jenkins.assertLogContains('Starting ECU-TEST...', run)
    }

    def 'Run pipeline keep instances'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ECU-TEST', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', keepInstance: true }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Re-using running instance ', run)
    }

    def 'Run pipeline keep TT tools open'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ECU-TEST', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', stopUndefinedTools: false }",
                    true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogNotContains('Re-using running instance ', run)
            jenkins.assertLogNotContains('Stop TraceTronic tool instances.', run)
            jenkins.assertLogContains('Starting ECU-TEST...', run)
    }
}
