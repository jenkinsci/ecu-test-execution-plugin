/*
* Copyright (c) 2024-2025 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/
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

class ProvideExecutionLogsStepIT extends IntegrationTestBase {
    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            ProvideExecutionLogsStep before = new ProvideExecutionLogsStep()

        when:
            ProvideExecutionLogsStep after = new StepConfigTester(jenkins).configRoundTrip(before)

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
            ProvideExecutionLogsStep step = new ProvideExecutionLogsStep()

        when:
            step.setPublishConfig(publishConfig)
            step.setReportIds(["reportId", "", "reportId3"])

        then:
            st.assertRoundTrip(step, "ttProvideLogs publishConfig: [allowMissing: true, " +
                    "keepAll: false, timeout: 10], reportIds: ['reportId', 'reportId3']")
        when:
            step.setPublishConfig(publishConfig)
            step.setReportIds("reportId2,,reportId4")
        then:
            st.assertRoundTrip(step, "ttProvideLogs publishConfig: [allowMissing: true, " +
                    "keepAll: false, timeout: 10], reportIds: ['reportId2', 'reportId4']")
    }

    def 'Run pipeline default'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs()}", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing ecu.test Logs. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
    }

    def 'Run pipeline allow missing logs'() {
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs(publishConfig: [allowMissing: true])}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogNotContains("Successfully added ecu.test Logs to jenkins.", run)
    }

    def 'Run pipeline to provide all logs successfully'() {
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
                    ['reportId/ecu.test_out.log', 'reportId/ecu.test_err.log'],
                    ['reportId3/ecu.test_out.log', 'reportId3/ecu.test_err.log']
            ]
        and:
            GroovySpy(ProvideFilesBuilder, global: true) {
                archiveFiles(*_) >> true
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs()}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("Providing all ecu.test Logs...", run)
            jenkins.assertLogContains("Providing ecu.test Logs for report reportDir1...", run)
            jenkins.assertLogContains("Providing ecu.test Logs for report reportDir2...", run)
            jenkins.assertLogNotContains("No files found to archive!", run)
            jenkins.assertLogContains("Successfully added ecu.test Logs to jenkins.", run)
    }

    def 'Run pipeline unstable for log ids not found with warning prints'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(*_) >> null
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs publishConfig: [allowMissing: true, failOnError: false], " +
                    "reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("[WARNING] Report with id reportId could not be found!", run)
            jenkins.assertLogContains("[WARNING] Report with id reportId3 could not be found!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("Build result set to UNSTABLE due to warnings.", run)
    }

    def 'Run pipeline with log ids successfully no warnings printed'() {
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
                    ['reportId/ecu.test_out.log', 'reportId/ecu.test_err.log'],
                    ['reportId3/ecu.test_out.log', 'reportId3/ecu.test_err.log']
                    ]
        and:
            GroovySpy(ProvideFilesBuilder, global: true) {
                archiveFiles(*_) >> true
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Logs for report reportDir1...", run)
            jenkins.assertLogContains("Providing ecu.test Logs for report reportDir2...", run)
            jenkins.assertLogContains("Successfully added ecu.test Logs to jenkins.", run)
            jenkins.assertLogNotContains("[WARNING] Report with id", run)
            jenkins.assertLogNotContains("No files found to archive!", run)
    }

    def 'Run pipeline with fail on error missing log ids'() {
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
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Logs failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing report reportId3. " +
                    "Set Pipeline step property 'Fail On Error' to 'false' to ignore missing reports.", run)
    }

    def 'Run pipeline with fail on error missing logs download'() {
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
                    ['reportId/ecu.test_out.log', 'reportId/ecu.test_err.log'],
                    ['ignore']
            ]
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs reportIds: ['reportId', '', 'reportId3']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Logs for report reportDir1...", run)
            jenkins.assertLogContains("Providing ecu.test Logs for report reportDir2...", run)
            jenkins.assertLogContains("Providing ecu.test Logs failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to failing " +
                    "download of reportId3. Set Pipeline step property 'Fail On Error' to 'false' to " +
                    "ignore any download error.", run)
    }

    def 'Run pipeline with fail missing logs file in .zip'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientV2 restApiClientV2Mock = GroovyMock(RestApiClientV2, global: true)
            RestApiClientFactory.getRestApiClient(*_) >> restApiClientV2Mock
            restApiClientV2Mock.getReport(_) >> new ReportInfo('reportId', 'reportDir', 'result', [])
            restApiClientV2Mock.downloadReportFolder(_) >> new File('src/test/resources/report.zip')
        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(*_) >> []
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs reportIds: ['reportId']}", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Logs for report reportDir...", run)
            jenkins.assertLogContains("reportDir is missing one or all log files!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing ecu.test Logs. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
            jenkins.assertLogContains("Providing ecu.test Logs failed!", run)
    }

    def 'Run pipeline exceeds timeout'() {
        int timeout = 1
        given:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node {ttProvideLogs(publishConfig: [timeout: ${timeout}])}", true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("Execution has exceeded the configured timeout of ${timeout} seconds", run)
            jenkins.assertLogContains("Providing ecu.test Logs failed!", run)
    }
}
