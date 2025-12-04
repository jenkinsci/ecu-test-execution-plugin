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

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
            step.setSelectedReportTypes("ATX")
            step.setReportIds(["reportId", "", "reportId3"])
        then:
            st.assertRoundTrip(step, "ttProvideGeneratedReports publishConfig: [allowMissing: true, " +
                    "keepAll: false, timeout: 10], reportIds: ['reportId', 'reportId3'], selectedReportTypes: 'ATX'")
        when:
            step.setPublishConfig(publishConfig)
            step.setSelectedReportTypes("ATX")
            step.setReportIds("  reportId2  ,,reportId4")
        then:
            st.assertRoundTrip(step, "ttProvideGeneratedReports publishConfig: [allowMissing: true, " +
                    "keepAll: false, timeout: 10], reportIds: ['reportId2', 'reportId4'], selectedReportTypes: 'ATX'")
    }

    def 'Run pipeline default'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports failed!", run)
            jenkins.assertLogContains("ERROR: Could not find a ecu.test REST api for host: localhost:5050", run)
    }

    def 'Run pipeline do not allow missing logs'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> []
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing Generated " +
                "ecu.test Reports. Adjust AllowMissing step property if this is not intended.", run)
    }

    def 'Run pipeline allow missing generated reports'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getAllReports() >> []
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports(publishConfig: [allowMissing: true])}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogNotContains("Successfully added Generated ecu.test Reports to jenkins.", run)
    }

    def 'Run pipeline to provide all generated reports successfully'() {
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
            def zipEntries = [
                    new ZipEntry('ATX/reportId.xml'),
                    new ZipEntry('JSON/reportId3.json')
            ]
            def zipFileStub1 = GroovyStub(ZipFile) { entries() >> Collections.enumeration(zipEntries) }
            GroovyMock(ZipFile, global: true)
            new ZipFile(_) >>> [zipFileStub1]
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >>> [
                    ['reportId/ecu.test.json', 'reportId/ecu.test.xml'],
                    ['reportId3/ecu.test.json', 'reportId3/ecu.test.xml']
            ]
        and:
            GroovySpy(ProvideFilesBuilder, global: true) {
                archiveFiles(*_) >> true
        }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing all Generated ecu.test Reports...", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir1...", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir2...", run)
            jenkins.assertLogNotContains("Could not find any matching generated report files in reportDir!", run)
            jenkins.assertLogNotContains("No files found to archive!", run)
            jenkins.assertLogContains("Successfully added Generated ecu.test Reports to jenkins.", run)
    }

    def 'Run pipeline to provide all generated reports, no matching reports found'() {
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
            def zipEntries = [
                    new ZipEntry('INVALID/reportId.xml')
            ]
            def zipFileStub1 = GroovyStub(ZipFile) { entries() >> Collections.enumeration(zipEntries) }
            GroovyMock(ZipFile, global: true)
            new ZipFile(_) >> zipFileStub1
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >>> [
                    ['reportId/ecu.test.xml', 'reportId/ecu.test.xml']
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing all Generated ecu.test Reports...", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir1...", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir2...", run)
            jenkins.assertLogContains("Could not find any matching generated report files in reportDir1!", run)
            jenkins.assertLogContains("Could not find any matching generated report files in reportDir2!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing Generated " +
                    "ecu.test Reports. Adjust AllowMissing step property if this is not intended.", run)
    }

    def 'Run pipeline unstable for generated report ids not found with warning prints'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(*_) >> null
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports publishConfig: [allowMissing: true, failOnError: false], " +
                    "reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("[WARNING] Report with id reportId could not be found!", run)
            jenkins.assertLogContains("[WARNING] Report with id reportId3 could not be found!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("Build result set to UNSTABLE due to warnings.", run)
    }

    def 'Run pipeline with generated report ids successfully no warnings printed'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(_) >>> [
                    new ReportInfo('reportId', 'reportDir1', 'result', []),
                    new ReportInfo('reportId3', 'reportDir2', 'result', [])
            ]
            restApiClientV2Mock.downloadReportFolder(*_) >>> [
                    new File('src/test/resources/report.zip'),
                    new File('src/test/resources/report3.zip')
            ]
        and:
            def zipEntries = [
                    new ZipEntry('ATX/reportId.xml'),
                    new ZipEntry('JSON/reportId3.json')
            ]
            def zipFileStub = GroovyStub(ZipFile) {
                entries() >> Collections.enumeration(zipEntries)
            }
            GroovyMock(ZipFile, global: true)
            new ZipFile(_) >> zipFileStub
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >>> [
                    ['reportId/ecu.test.json', 'reportId/ecu.test.xml'],
                    ['reportId3/ecu.test.json', 'reportId3/ecu.test.xml']
            ]
        and:
            GroovySpy(ProvideFilesBuilder, global: true) {
                archiveFiles(*_) >> true
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir1...", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir2...", run)
            jenkins.assertLogContains("Successfully added Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogNotContains("[WARNING] Report with id", run)
            jenkins.assertLogNotContains("No files found to archive!", run)
    }

    def 'Run pipeline with fail on error missing generated report ids'() {
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
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing report reportId3. " +
                    "Set Pipeline step property 'Fail On Error' to 'false' to ignore missing reports.", run)
    }

    def 'Run pipeline with fail on error missing generated reports download'() {
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
            def zipEntries = [
                    new ZipEntry('ATX/reportId.xml'),
                    new ZipEntry('JSON/reportId.json')
            ]
            def zipFileStub = GroovyStub(ZipFile) {
                entries() >> Collections.enumeration(zipEntries)
            }
            GroovyMock(ZipFile, global: true)
            new ZipFile(_) >> zipFileStub
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >>> [
                ['reportId/ecu.test.json', 'reportId/ecu.test.xml'],
                ['reportId/ecu.test_out.log', 'reportId/ecu.test_err.log'],
                ['ignore']
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports reportIds: ['reportId', '', 'reportId3']}", true))
            expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir1...", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports for report reportDir2...", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to failing " +
                    "download of reportId3. Set Pipeline step property 'Fail On Error' to 'false' to " +
                    "ignore any download error.", run)
    }

    def 'Run pipeline exceeds timeout'() {
        int timeout = 1

        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideGeneratedReports(publishConfig: [timeout: ${timeout}])}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Execution has exceeded the configured timeout of ${timeout} seconds", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports failed!", run)
    }
}
