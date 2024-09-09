/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.views

import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import hudson.FilePath
import hudson.Launcher
import hudson.model.FreeStyleBuild
import hudson.model.TaskListener
import hudson.tasks.ArtifactArchiver

class ProvideFilesActionViewIT extends IntegrationTestBase {

    def 'Get provide actions view for build'() {
        given:
            FreeStyleBuild build = jenkins.createFreeStyleProject().scheduleBuild2(0).get()
            ProvideFilesActionView actionView = new ProvideFilesActionView(build.externalizableId, "test-logs", "myIcon")
        when:
            build.addAction(actionView)
        then:
            build.getArtifacts().size() == 0
            build.getActions(ProvideFilesActionView.class).size() == 1
    }

    def 'No provide actions for build initially'() {
        given:
            FreeStyleBuild build = jenkins.createFreeStyleProject().scheduleBuild2(0).get()

        expect:
            build.getActions(ProvideFilesActionView.class).size() == 0
    }

    def "Get hierarchical structure of artifact paths"() {
        given:
            FreeStyleBuild build = jenkins.createFreeStyleProject().scheduleBuild2(0).get()
            def dirName = "test-logs"

            addMockArtifacts(build, [
                    "${dirName}/file1.log",
                    "${dirName}/dir1/file2.log",
                    "${dirName}/dir1/dir2/file3.log",
                    "other-dir/file4.log"
            ])

            ProvideFilesActionView actionView = new ProvideFilesActionView(build.externalizableId, dirName, "myIcon")

        when:
            Map<String, Object> result = actionView.getLogPathMap()

        then:
            result.size() == 2
            result.containsKey("file1.log")
            result.containsKey("dir1")
            result["dir1"] instanceof Map
            result["dir1"].containsKey("file2.log")
            result["dir1"].containsKey("dir2")
            result["dir1"]["dir2"] instanceof Map
            result["dir1"]["dir2"].containsKey("file3.log")
            !result.containsKey("other-dir")
    }

    private void addMockArtifacts(FreeStyleBuild build, List<String> paths) {
        paths.each { path ->
            FilePath artifactFile = new FilePath(build.workspace, path)
            artifactFile.write("test content", "UTF-8")
        }

        Launcher launcher = jenkins.createLocalLauncher()
        ArtifactArchiver archiver = new ArtifactArchiver("**/*")
        archiver.perform(build, build.workspace, build.getEnvironment(TaskListener.NULL), launcher, TaskListener.NULL)
        build.getArtifacts()
    }
}
