package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.model.Result
import hudson.tasks.junit.TestResult
import hudson.tasks.test.PipelineTestDetails
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester


class ProvideUnitReportsStepExecutionIT extends IntegrationTestBase {

    def 'Default config round trip'() {
        given:
            ProvideUnitReportsStep before = new ProvideUnitReportsStep()
        when:
            ProvideUnitReportsStep after = new StepConfigTester(jenkins).configRoundTrip(before)
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
            ProvideUnitReportsStep step = new ProvideUnitReportsStep()
        when:
            step.setPublishConfig(publishConfig)
            step.setUnstableThreshold(10.0)
            step.setFailedThreshold(20.0)
            step.setReportGlob("**.xml")
            step.setReportIds(["rid1", "rid2"])
        then:
            st.assertRoundTrip(step, "ttProvideUnitReports failedThreshold: 20.0, publishConfig: [allowMissing: " +
                    "true, keepAll: false, timeout: 10], reportGlob: '**.xml', reportIds: ['rid1', 'rid2'], " +
                    "unstableThreshold: 10.0")
    }

    def 'Run pipeline default'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> []
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideUnitReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Unit Reports to jenkins.", run)
            jenkins.assertLogContains("Providing Unit Reports failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing test results. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
    }

    def 'Run pipeline to add test results successfully'() {
        given:
            final String reportId = "Test"
            final File reportFile = new File('src/test/resources/test.zip')
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> [new ReportInfo(reportId, 'reportDir1', 'result', [])]
            restApiClientV2Mock.downloadReportFolder(reportId) >> reportFile
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByGlobPattern(reportFile, _ as String, _ as String) >> ['test/junit-report.xml']
        and:
            TestResult result = GroovySpy(global: true, {
                parse(_ as File, _ as PipelineTestDetails) >> _
                getTotalCount() >> 100
            })
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideUnitReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Unit Reports to jenkins.", run)
            jenkins.assertLogContains("Providing all Unit Reports...", run)
            jenkins.assertLogContains("Providing Unit Reports for report reportDir1...", run)
            jenkins.assertLogContains("Successfully added test results to Jenkins.", run)
    }

    def 'Run pipeline unstable by threshold'() {
        given:
            final String reportId = "Test"
            final File reportFile = new File('src/test/resources/test.zip')
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> [new ReportInfo(reportId, 'reportDir1', 'result', [])]
            restApiClientV2Mock.downloadReportFolder(reportId) >> reportFile
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByGlobPattern(reportFile, _ as String, _ as String) >> ['test/junit-report.xml']
        and:
            TestResult result = GroovySpy(global: true, {
                parse(_ as File, _ as PipelineTestDetails) >> _
                getFailCount() >> 11
                getTotalCount() >> 100
            })
        and:
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
        job.setDefinition(new CpsFlowDefinition("node {ttProvideUnitReports failedThreshold: 20.0, unstableThreshold: 10.0}", true))
        expect:
        WorkflowRun run = jenkins.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0).get())
        jenkins.assertLogContains("Providing Unit Reports to jenkins.", run)
        jenkins.assertLogContains("Providing all Unit Reports...", run)
        jenkins.assertLogContains("Providing Unit Reports for report reportDir1...", run)
        jenkins.assertLogContains("Successfully added test results to Jenkins.", run)
        jenkins.assertLogContains("Build result set to ${Result.UNSTABLE.toString()} due to percentage of failed tests is higher than the configured threshold.", run)
    }

    def 'Fail pipeline by threshold'() {
        given:
            final String reportId = "Test"
            final File reportFile = new File('src/test/resources/test.zip')
        and:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> [new ReportInfo(reportId, 'reportDir1', 'result', [])]
            restApiClientV2Mock.downloadReportFolder(reportId) >> reportFile
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByGlobPattern(reportFile, _ as String, _ as String) >> ['test/junit-report.xml']
        and:
            TestResult result = GroovySpy(global: true, {
                parse(_ as File, _ as PipelineTestDetails) >> _
                getFailCount() >> 21
                getTotalCount() >> 100
            })
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideUnitReports failedThreshold: 20.0, unstableThreshold: 10.0}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Unit Reports to jenkins.", run)
            jenkins.assertLogContains("Providing all Unit Reports...", run)
            jenkins.assertLogContains("Providing Unit Reports for report reportDir1...", run)
            jenkins.assertLogContains("Successfully added test results to Jenkins.", run)
            jenkins.assertLogContains("Build result set to ${Result.FAILURE.toString()} due to percentage of failed tests is higher than the configured threshold.", run)
    }
}
