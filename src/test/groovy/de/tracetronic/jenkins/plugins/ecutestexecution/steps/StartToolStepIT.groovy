/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
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
        String executablePath = Functions.isWindows() ? 'C:\\ecutest\\ECU-TEST.exe' : 'bin/ecu-test'
        String executablePathV2 = Functions.isWindows() ? 'C:\\ecutest\\ecu.test.exe' : 'bin/ecu.test'
        etDescriptor.setInstallations(new ETInstallation('ECU-TEST', executablePath, JenkinsRule.NO_PROPERTIES),
                new ETInstallation('ecu.test', executablePathV2, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            StartToolStep before = new StartToolStep(toolName)
        when:
            StartToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
        where:
            toolName = 'ecu.test'
    }

    def 'Config round trip'() {
        given:
            StartToolStep before = new StartToolStep(toolName)
            before.setWorkspaceDir('workspace')
            before.setSettingsDir('settings')
            before.setTimeout(120)
            before.setKeepInstance(true)
            before.setStopUndefinedTools(false)
        when:
            StartToolStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
        where:
            toolName = 'ecu.test'
    }

    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            StartToolStep step = new StartToolStep(toolName)
        then:
            st.assertRoundTrip(step, "ttStartTool '${toolName}'")
        when:
            step.setWorkspaceDir('workspace')
        then:
            st.assertRoundTrip(step, "ttStartTool toolName: '${toolName}', workspaceDir: 'workspace'")
        when:
            step.setSettingsDir('settings')
        then:
            st.assertRoundTrip(step, "ttStartTool settingsDir: 'settings', toolName: '${toolName}', " +
                    "workspaceDir: 'workspace'")
        when:
            step.setTimeout(120)
        then:
            st.assertRoundTrip(step, "ttStartTool settingsDir: 'settings', timeout: 120, toolName: '${toolName}', " +
                    "workspaceDir: 'workspace'")
        when:
            step.setKeepInstance(true)
        then:
            st.assertRoundTrip(step, "ttStartTool keepInstance: true, settingsDir: 'settings', timeout: 120, " +
                    "toolName: '${toolName}', workspaceDir: 'workspace'")
        when:
            step.setStopUndefinedTools(false)
        then:
            st.assertRoundTrip(step, "ttStartTool keepInstance: true, settingsDir: 'settings', " +
                    "stopUndefinedTools: false, timeout: 120, toolName: '${toolName}', workspaceDir: 'workspace'")
        where:
            toolName = 'ecu.test'
    }

    def 'Run pipeline: Settings dir does not exist'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String tempDirString = tempDir.getPath().replace('\\', '/')

            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')

            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${tempDirString}', settingsDir: '${tempDirString}/foo' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("ecu.test settings directory created at ${tempDirString}/foo", run)
            jenkins.assertLogContains('Starting ecu.test...', run)
            Files.exists(Paths.get("${tempDirString}/foo"))
    }

    def 'Run pipeline'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: '${toolName}', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Stop tracetronic tool instances.', run)
            jenkins.assertLogContains("Starting ${toolName}...", run)
        where:
            toolName = 'ecu.test'
    }

    def 'Run pipeline keep instances'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', keepInstance: true, timeout: 5 }", true))
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
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', stopUndefinedTools: false }",
                    true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogNotContains('Re-using running instance ', run)
            jenkins.assertLogNotContains('Stop tracetronic tool instances.', run)
            jenkins.assertLogContains('Starting ecu.test...', run)
    }
}
