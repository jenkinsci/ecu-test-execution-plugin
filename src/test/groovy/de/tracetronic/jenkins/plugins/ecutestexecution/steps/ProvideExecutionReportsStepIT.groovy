package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import hudson.Functions
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule

class ProvideExecutionReportsStepIT extends IntegrationTestBase {
    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            ProvideExecutionReportsStep before = new ProvideExecutionReportsStep()

        when:
            ProvideExecutionReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)

        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }
    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
            PublishConfig publishConfig = new PublishConfig()
            publishConfig.setTimeout(10)
            publishConfig.setKeepAll(false)
            publishConfig.setAllowMissing(true)
            ProvideExecutionReportsStep step = new ProvideExecutionReportsStep()

        when:
            step.setPublishConfig(publishConfig)

        then:
            st.assertRoundTrip(step, "ttProvideReports(publishConfig: [allowMissing: true, keepAll: false, timeout: 10])")
    }

    def 'Run pipeline default'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports()}", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("[WARNING] No files found!", run)
            jenkins.assertLogContains("ERROR: Build Result set to FAILURE due to missing ecu.test Reports. Adjust AllowMissing step property if this is not intended.", run)
    }

    def 'Run pipeline allow missing reports'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports(publishConfig: [allowMissing: true])}", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("[WARNING] No files found!", run)
            jenkins.assertLogNotContains("Successfully added ecu.test Reports to jenkins.", run)
    }

    def 'Run pipeline exceeds timeout'() {
        given:
            int timeout = 1
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports(publishConfig: [timeout: ${timeout}])}", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Execution has exceeded the configured timeout of ${timeout} seconds", run)
            jenkins.assertLogContains("Providing ecu.test Reports failed!", run)
    }
}
