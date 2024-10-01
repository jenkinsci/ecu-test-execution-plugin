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

class ProvideGeneratedReportsStepIT extends IntegrationTestBase {
    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            ProvideGeneratedReportsStep before = new ProvideGeneratedReportsStep()
        when:
            ProvideGeneratedReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
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
            ProvideGeneratedReportsStep step = new ProvideGeneratedReportsStep()
        when:
            step.setPublishConfig(publishConfig)
            step.setIncludePattern("test")
        then:
            st.assertRoundTrip(step, "ttProvideGeneratedReports includePattern: 'test', publishConfig: [allowMissing: true, keepAll: false, timeout: 10]")
    }

    def 'Run pipeline default'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing generated-ecu.test-reports to jenkins.", run)
            jenkins.assertLogContains("[WARNING] No files found!", run)
            jenkins.assertLogContains("ERROR: Build Result set to FAILURE due to missing generated-ecu.test-reports. Adjust AllowMissing step property if this is not intended.", run)
    }

    def 'Run pipeline allow missing reports'() {
        given:
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
        job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports(publishConfig: [allowMissing: true])}", true))
        expect:
        WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
        jenkins.assertLogContains("Providing generated-ecu.test-reports to jenkins.", run)
        jenkins.assertLogContains("[WARNING] No files found!", run)
        jenkins.assertLogNotContains("Successfully added generated-ecu.test-reports to jenkins.", run)
    }

    def 'Run pipeline exceeds timeout'() {
        int timeout = 1
        given:
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
        job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports(publishConfig: [timeout: ${timeout}])}", true))
        expect:
        WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
        jenkins.assertLogContains("Providing generated-ecu.test-reports to jenkins.", run)
        jenkins.assertLogContains("Execution has exceeded the configured timeout of ${timeout} seconds", run)
        jenkins.assertLogContains("Providing generated-ecu.test-reports failed!", run)
    }
}
