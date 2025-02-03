/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ProcessUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
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
    ETInstallation.DescriptorImpl etDescriptor

    def setup() {
        etDescriptor = jenkins.jenkins
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
        and:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killTTProcesses(_) >> true
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
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
        and:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killTTProcesses(_) >> true
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
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
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
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
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogNotContains('Re-using running instance ', run)
            jenkins.assertLogNotContains('Stop tracetronic tool instances.', run)
            jenkins.assertLogContains('Starting ecu.test...', run)
    }

    def 'Run pipeline: Workspace directory does not exist'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String tempDirString = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${tempDirString}/foo', settingsDir: '${tempDirString}' }", true))
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("ecu.test workspace directory at ${tempDirString}/foo does not exist! " +
                    "Please ensure that the path is correctly set and it refers to the desired directory.", run)
    }

    def 'Run pipeline: ecu.test connection valid'() {
        given:
            String toolName = 'ecu.test'
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: '${toolName}', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', timeout: 10, " +
                    "stopUndefinedTools: false }", true))
        and:
            RestApiClient restApiClientMock = GroovyMock(RestApiClient)
            GroovySpy(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(_, _, _) >> restApiClientMock
        and:
            Process processMock = GroovyMock(Process)
            ProcessBuilder processBuilderMock = GroovySpy(ProcessBuilder, global: true)
            new ProcessBuilder(_) >> processBuilderMock
            processBuilderMock.command(_) >> processBuilderMock
            processBuilderMock.start() >> processMock
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("${toolName} started successfully.", run)
    }

    def 'Run pipeline: Timeout exceeded stopping tracetronic tools'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', timeout: 10 }", true))
        and:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killTTProcesses(_) >> false
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("Timeout of 10 seconds exceeded for stopping tracetronic tools! " +
                    "Please ensure that tracetronic tools are not already stopped or "  +
                    "blocked by another process.", run)
    }

    def 'Run pipeline: No valid license found'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', timeout: 2}", true))
        and:
            Process processMock = GroovyMock(Process)
            ProcessBuilder processBuilderMock = GroovySpy(ProcessBuilder, global: true)
            new ProcessBuilder(_) >> processBuilderMock
            processBuilderMock.command(_) >> processBuilderMock
            processBuilderMock.start() >> processMock
            processMock.exitValue() >> 99
        and:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killTTProcesses(_) >> true
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("No valid license found for ecu.test! " +
                    "Please ensure the license is not expired or corrupted.", run)
    }

    def 'Run pipeline: Start tool with error code for unavailable port'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', timeout: 2}", true))
        and:
            GroovySpy(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(_, _, _) >> null
        and:
            Process processMock = GroovyMock(Process)
            ProcessBuilder processBuilderMock = GroovySpy(ProcessBuilder, global: true)
            new ProcessBuilder(_) >> processBuilderMock
            processBuilderMock.command(_) >> processBuilderMock
            processBuilderMock.start() >> processMock
            processMock.exitValue() >> 8
        and:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killTTProcesses(_) >> true
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("ecu.test did not start correctly and stopped with exit code 8 " +
                    "within the timeout of 2 seconds.", run)
    }

    def 'Run pipeline: IllegalThreadStateException'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}', timeout: 2 }", true))
        and:
            GroovySpy(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(_, _, _) >> { throw new ApiException("") }
        and:
            Process processMock = GroovyMock(Process)
            ProcessBuilder processBuilderMock = GroovySpy(ProcessBuilder, global: true) as ProcessBuilder
            new ProcessBuilder(_) >> processBuilderMock
            processBuilderMock.command(_) >> processBuilderMock
            processBuilderMock.start() >> processMock
            processMock.exitValue() >> { throw new IllegalThreadStateException("")}
        and:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killTTProcesses(_) >> true
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        then:
            1* processMock.destroy()
            jenkins.assertLogContains("Timeout of 2 seconds exceeded for connecting to ecu.test! " +
                    "Please ensure the tool is correctly configured and consider restarting it.", run)
    }


    def 'Run pipeline: return and print result'() {
        given:
            File tempDir = File.createTempDir()
            tempDir.deleteOnExit()
            String workspaceDir = tempDir.getPath().replace('\\', '/')
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStartTool toolName: 'ecu.test', " +
                    "workspaceDir: '${workspaceDir}', settingsDir: '${workspaceDir}' }", true))
        and:
            Process processMock = GroovyMock(Process)
            ProcessBuilder processBuilderMock = GroovySpy(ProcessBuilder, global: true)
            new ProcessBuilder(_) >> processBuilderMock
            processBuilderMock.command(_) >> processBuilderMock
            processBuilderMock.start() >> processMock
            processMock.isAlive() >> true
        and:
            GroovyMock(ProcessUtil, global: true)
            ProcessUtil.killTTProcesses(_) >> true
        and:
            RestApiClient restApiClientMock = GroovyMock(RestApiClient)
            GroovySpy(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient(_, _, _) >> restApiClientMock
        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
        then:
            jenkins.assertLogContains("ecu.test started successfully.", run)
            jenkins.assertLogContains("-> installationName: ecu.test", run)
            jenkins.assertLogContains("-> toolExePath", run)
            jenkins.assertLogContains("-> workSpaceDirPath: ${workspaceDir}", run)
            jenkins.assertLogContains("-> settingsDirPath: ${workspaceDir}", run)
    }
}
