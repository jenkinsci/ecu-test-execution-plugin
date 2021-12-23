/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.views

import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunProjectAction
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import hudson.model.FreeStyleBuild

class RunProjectActionViewIT extends IntegrationTestBase {

    def 'No project result actions for build'() {
        given:
            FreeStyleBuild build = jenkins.createFreeStyleProject().scheduleBuild2(0).get()
            RunProjectActionView actionView = new RunProjectActionView(build)
        when:
            build.addAction(actionView)
        then:
            actionView.getTestActions().size() == 0
    }

    def 'Project result actions for build'() {
        given:
            RunProjectAction packageAction = new RunProjectAction('test.prj',
                    new TestConfig(), new TestResult(null, null, null))
        when:
            FreeStyleBuild build = jenkins.createFreeStyleProject()
                    .scheduleBuild2(0, null, Collections.singletonList(packageAction)).get()
        then:
            build.getAction(RunProjectAction) != null
            build.getAction(RunProjectActionView) != null
    }
}
