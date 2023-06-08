/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule

class StopToolStepIT extends IntegrationTestBase {

    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        etDescriptor.setInstallations(new ETInstallation('ECU-TEST', 'C:\\ECU-TEST\\ECU-TEST.exe', JenkinsRule.NO_PROPERTIES))
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
    }

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttStopTool 'ECU-TEST' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Stopping ECU-TEST...', run)
    }
}
