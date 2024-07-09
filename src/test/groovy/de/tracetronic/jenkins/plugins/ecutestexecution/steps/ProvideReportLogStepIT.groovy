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

class ProvideReportLogStepIT extends IntegrationTestBase {
    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            ProvideReportLogsStep before = new ProvideReportLogsStep()
        when:
            ProvideReportLogsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }
    def 'Snippet generator'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            ProvideReportLogsStep step = new ProvideReportLogsStep()
        then:
            st.assertRoundTrip(step, "ttProvideReportLogs()")
    }

    def 'Run pipeline'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReportLogs()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test report logs to jenkins.", run)
    }
}
