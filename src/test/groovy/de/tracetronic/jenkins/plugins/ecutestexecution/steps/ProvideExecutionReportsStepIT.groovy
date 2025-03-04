package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.ProvideFilesBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
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
            step.setReportIds(["reportId", "", "reportId3"])
        then:
            st.assertRoundTrip(step, "ttProvideReports publishConfig: [allowMissing: true, keepAll: false, " +
                    "timeout: 10], reportIds: ['reportId', 'reportId3']")
        when:
            step.setPublishConfig(publishConfig)
            step.setReportIds("reportId,,reportId3")
        then:
            st.assertRoundTrip(step, "ttProvideReports publishConfig: [allowMissing: true, keepAll: false, " +
                    "timeout: 10], reportIds: ['reportId', 'reportId3']")
    }

    def 'Run pipeline default'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports()}", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing ecu.test Reports. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
    }


    def 'Run pipeline allow missing reports'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports(publishConfig: [allowMissing: true])}", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogNotContains("Successfully added ecu.test Reports to jenkins.", run)
    }

    def 'Run pipeline to provide all reports successfully'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> [
                    new ReportInfo('reportId', 'reportDir1', 'result', []),
                    new ReportInfo('reportId3', 'reportDir2', 'result', [])
            ]
            restApiClientV2Mock.downloadReportFolder(*_) >>> [
                    new File('src/test/resources/report.zip'),
                    new File('src/test/resources/report3.zip')
            ]
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >>> [
                    ['reportId/ecu.test.prf'],
                    ['reportId3/ecu.test.trf']
            ]
        and:
            GroovySpy(ProvideFilesBuilder, global: true) {
                archiveFiles(*_) >> true
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing all ecu.test Reports...", run)
            jenkins.assertLogContains("Providing ecu.test Reports for report reportDir1...", run)
            jenkins.assertLogContains("Providing ecu.test Reports for report reportDir2...", run)
            jenkins.assertLogContains("Successfully added ecu.test Reports to jenkins.", run)
            jenkins.assertLogNotContains("No files found to archive!", run)
    }

    def 'Run pipeline unstable for report ids not found with warning prints'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(*_) >> null
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports publishConfig: [allowMissing: true, failOnError: false], " +
                    "reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("[WARNING] Report with id reportId could not be found!", run)
            jenkins.assertLogContains("[WARNING] Report with id reportId3 could not be found!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("Build result set to UNSTABLE due to warnings.", run)
    }

    def 'Run pipeline with report ids successfully no warnings printed'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(*_) >>> [
                    new ReportInfo('reportId', 'reportDir1', 'result', []),
                    new ReportInfo('reportId3', 'reportDir2', 'result', [])
            ]
            restApiClientV2Mock.downloadReportFolder(*_) >>> [
                    new File('src/test/resources/report.zip'),
                    new File('src/test/resources/report3.zip')
            ]
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >>> [
                    ['reportId/ecu.test.prf', 'reportId/ecu.test.trf'],
                    ['reportId3/ecu.test.prf', 'reportId3/ecu.test.trf']
            ]
        and:
            GroovySpy(ProvideFilesBuilder, global: true) {
                archiveFiles(*_) >> true
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Reports for report reportDir1...", run)
            jenkins.assertLogContains("Providing ecu.test Reports for report reportDir2...", run)
            jenkins.assertLogContains("Successfully added ecu.test Reports to jenkins.", run)
            jenkins.assertLogNotContains("[WARNING] Report with id", run)
            jenkins.assertLogNotContains("No files found to archive!", run)
    }

    def 'Run pipeline with fail on error missing report ids'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(*_) >>> [
                    new ReportInfo('reportId', 'reportDir', 'result', []),
                    null
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Reports failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing report reportId3. " +
                    "Set Pipeline step property 'Fail On Error' to 'false' to ignore missing reports.", run)
    }

    def 'Run pipeline with fail on error missing report download'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(*_) >>> [
                    new ReportInfo('reportId', 'reportDir1', 'result', []),
                    new ReportInfo('reportId3', 'reportDir2', 'result', [])
            ]
            restApiClientV2Mock.downloadReportFolder(*_) >>> [
                    new File('src/test/resources/report.zip'),
                    null
            ]
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >>> [
                    ['reportId/ecu.test.prf', 'reportId/ecu.test.trf'],
                    ['ignore']
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Reports for report reportDir1...", run)
            jenkins.assertLogContains("Providing ecu.test Reports for report reportDir2...", run)
            jenkins.assertLogContains("Providing ecu.test Reports failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to failing " +
                    "download of reportId3. Set Pipeline step property 'Fail On Error' to 'false' to " +
                    "ignore any download error.", run)
    }

    def 'Run pipeline missing report files'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> [
                    new ReportInfo('reportId', 'reportDir', 'result', [])
            ]
            restApiClientV2Mock.downloadReportFolder(*_) >> new File('src/test/resources/report.zip')
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >> []

        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing all ecu.test Reports...", run)
            jenkins.assertLogContains("Providing ecu.test Reports for report reportDir...", run)
            jenkins.assertLogContains("Could not find any report files in reportDir!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing ecu.test Reports. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
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
