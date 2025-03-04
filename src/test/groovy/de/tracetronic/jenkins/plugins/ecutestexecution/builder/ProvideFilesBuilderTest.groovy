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

        and:
            context.get(TaskListener) >> listener
            listener.getLogger() >> logger

        and:
            def builder = new ProvideFilesBuilder(context)

        when:
            def result = builder.archiveFiles([], "reportsDir", true, "viewIcon")

        then:
            !result
            1 * logger.println('No files found to archive!')
            1 * logger.flush()
    }

    def "Test archiveFiles and add action #addActionCalled time with current run actions are #actionsView"() {
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

        and:
            context.get(Run) >> run
            context.get(FilePath) >> workspace
            context.get(TaskListener) >> listener
            context.get(Launcher) >> launcher
            run.getArtifactManager() >> artifactManager
            run.getActions(_) >> actionsView
            workspace.getRemote() >> workspacePath
            workspace.child(_) >> outDir
            listener.getLogger() >> logger

        and:
            ProvideFilesBuilder builder = new ProvideFilesBuilder(context)

        when:
            def result = builder.archiveFiles(filePaths, "reportsDir", true, iconName)

        then:
            result
            1 * artifactManager.archive(_, _, _, _) >> { args ->
                assert args[3] == ["Reports/report1.trf": "Reports/report1.trf", "Reports/report2.prf": "Reports/report2.prf"]
            }
            addActionCalled * run.addAction(_) >> { args ->
                assert args[0] instanceof ProvideFilesActionView
                assert args[0].dirName == "reportsDir"
                assert args[0].iconFileName == "plugin/ecu-test-execution/images/file/${iconName}.svg"
            }
            0 * outDir.deleteRecursive()

        where:
            actionsView << [
                    null,
                    [],
                    [new ProvideFilesActionView('1', 'reportsDir', 'iconName')],
                    [new ProvideFilesActionView('1', 'logsDir', 'iconLogsName')]
                    ]
            addActionCalled << [1, 1, 0, 1]
    }

    def "Test archiveFiles keep artifacts #keep"() {
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

        and:
            context.get(Run) >> run
            context.get(FilePath) >> workspace
            context.get(TaskListener) >> listener
            context.get(Launcher) >> launcher
            run.getArtifactManager() >> artifactManager
            workspace.getRemote() >> workspacePath
            workspace.child(_) >> outDir
            listener.getLogger() >> logger

        and:
            def builder = new ProvideFilesBuilder(context)

        when:
            def result = builder.archiveFiles(filePaths, "reportsDir", keep, "viewIcon")

        then:
            result
            1 * artifactManager.archive(_, _, _, ["Reports/report.trf": "Reports/report.trf"])
            1 * run.addAction(_)
            deleteCalled * outDir.deleteRecursive()

        where:
            keep << [false, true]
            deleteCalled << [1, 0]
    }
}
