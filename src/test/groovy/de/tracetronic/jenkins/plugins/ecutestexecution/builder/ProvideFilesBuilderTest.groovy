/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.jenkins.plugins.ecutestexecution.views.ProvideFilesActionView
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import jenkins.model.ArtifactManager
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.Specification

class ProvideFilesBuilderTest extends Specification {

    def "Test archiveFiles no files found"() {
        given:
            def context = GroovyMock(StepContext)
            def listener = GroovyMock(TaskListener)
            def logger = GroovyMock(PrintStream)
            context.get(TaskListener) >> listener
            listener.getLogger() >> logger

            def builder = new ProvideFilesBuilder(context)

        when:
            def result = builder.archiveFiles([], "reportsDir", true, "viewIcon")

        then:
            !result
            1 * logger.println('[WARNING] No files found!')
            1 * logger.flush()
    }

    def "Test archiveFiles and add action successfully"() {
        given:
            def context = GroovyMock(StepContext)
            def run = GroovyMock(Run)
            def workspace = GroovyMock(FilePath)
            def listener = GroovyMock(TaskListener)
            def launcher = GroovyMock(Launcher)
            def artifactManager = GroovyMock(ArtifactManager)
            def outDir = GroovyMock(FilePath)
            def logger = GroovyMock(PrintStream)

            def workspacePath = "/path/to/remote/workspace"
            def filePaths = [
                    "${workspacePath}/Reports/report1.trf",
                    "${workspacePath}/Reports/report2.prf"
            ]
            def iconName = "viewIcon"

            context.get(Run) >> run
            context.get(FilePath) >> workspace
            context.get(TaskListener) >> listener
            context.get(Launcher) >> launcher
            run.getArtifactManager() >> artifactManager
            workspace.getRemote() >> workspacePath
            workspace.child(_) >> outDir
            listener.getLogger() >> logger

            ProvideFilesBuilder builder = new ProvideFilesBuilder(context)

        when:
            def result = builder.archiveFiles(filePaths, "reportsDir", true, iconName)

        then:
            result
            1 * artifactManager.archive(workspace, launcher, listener, _) >> { args ->
                assert args[3] == ["Reports/report1.trf": "Reports/report1.trf", "Reports/report2.prf": "Reports/report2.prf"]
            }
            1 * run.addAction(_) >> { args ->
                assert args[0] instanceof ProvideFilesActionView
                assert args[0].dirName == "reportsDir"
                assert args[0].iconFileName == "plugin/ecu-test-execution/images/file/${iconName}.svg"
            }
            0 * outDir.deleteRecursive()
    }

    def "Test archiveFiles delete reportsDir"() {
        given:
            def context = GroovyMock(StepContext)
            def run = GroovyMock(Run)
            def workspace = GroovyMock(FilePath)
            def listener = GroovyMock(TaskListener)
            def launcher = GroovyMock(Launcher)
            def artifactManager = GroovyMock(ArtifactManager)
            def outDir = GroovyMock(FilePath)
            def logger = GroovyMock(PrintStream)

            def workspacePath = "/path/to/remote/workspace"
            def filePaths = ["${workspacePath}/Reports/report.trf"]

            context.get(Run) >> run
            context.get(FilePath) >> workspace
            context.get(TaskListener) >> listener
            context.get(Launcher) >> launcher
            run.getArtifactManager() >> artifactManager
            workspace.getRemote() >> workspacePath
            workspace.child(_) >> outDir
            listener.getLogger() >> logger

            def builder = new ProvideFilesBuilder(context)

        when:
            def result = builder.archiveFiles(filePaths, "reportsDir", false, "viewIcon")

        then:
            result
            1 * artifactManager.archive(workspace, launcher, listener, ["Reports/report.trf": "Reports/report.trf"])
            1 * run.addAction(_)
            1 * outDir.deleteRecursive()
    }
}
