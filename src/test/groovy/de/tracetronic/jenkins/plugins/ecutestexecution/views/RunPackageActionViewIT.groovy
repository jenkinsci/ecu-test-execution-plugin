/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.views

import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunPackageAction
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import hudson.model.FreeStyleBuild

class RunPackageActionViewIT extends IntegrationTestBase {

    def 'No package result actions for build'() {
        given:
            FreeStyleBuild build = jenkins.createFreeStyleProject().scheduleBuild2(0).get()
            RunPackageActionView actionView = new RunPackageActionView(build)
        when:
            build.addAction(actionView)
        then:
            actionView.getTestActions().size() == 0
    }

    def 'Package result actions for build'() {
        given:
            RunPackageAction packageAction = new RunPackageAction('test.pkg',
                    new TestConfig(), new PackageConfig(null),
                    new AnalysisConfig(), new TestResult(null, null, null))
        when:
            FreeStyleBuild build = jenkins.createFreeStyleProject()
                    .scheduleBuild2(0, null, Collections.singletonList(packageAction)).get()
        then:
            build.getAction(RunPackageAction) != null
            build.getAction(RunPackageActionView) != null
    }
}
