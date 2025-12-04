/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.model.Result
import hudson.tasks.junit.TestResultAction
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.spock.Testcontainers

@Testcontainers
class ETV2ContainerTest extends ETContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ETV2ContainerTest.class)
    private static final String etLogsFolderName = 'ecu.test Logs'
    private static final String etReportsFolderName = 'ecu.test Reports'
    private static final String etGeneratedReportsFolderName = 'Generated ecu.test Reports'

    GenericContainer getETContainer() {
        return new GenericContainer<>(ET_V2_IMAGE_NAME)
                .withExposedPorts(ET_PORT)
                .withEnv("tracet_LICENSE", ET_LICENSE_SERVER)
                .withClasspathResourceMapping("workspace/.workspace", "${ET_WS_PATH}/.workspace",
                        BindMode.READ_ONLY)
                .withClasspathResourceMapping("workspace/Configurations",
                        "${ET_WS_PATH}/Configurations", BindMode.READ_ONLY)
                .withClasspathResourceMapping("workspace/Packages", "${ET_WS_PATH}/Packages",
                        BindMode.READ_ONLY)
                .withClasspathResourceMapping("workspace/UserPyModules", "${ET_WS_PATH}/UserPyModules",
                        BindMode.READ_ONLY)
                .withClasspathResourceMapping("workspace/localsettings.xml", "${ET_WS_PATH}/localsettings.xml",
                        BindMode.READ_ONLY)
                .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                .waitingFor(Wait.forHttp("/api/v2/live"))
    }

    String toPipelineScript(String innerScript) {
        return """
        node {
            withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                ${innerScript}
            }
        }
        """.stripIndent()
    }

    def "ttProvideLogs: Test for missing files"() {
        given: "a pipeline with log provider"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttProvideLogs()
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)
        then: "expect log information about failed pipeline run"
            jenkins.assertLogContains("Providing $etLogsFolderName to jenkins.", run)
            jenkins.assertLogContains("Providing all $etLogsFolderName...", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("Providing $etLogsFolderName failed!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing $etLogsFolderName. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
    }

    def "ttProvideLogs: Test for allowed missing files"() {
        given: "a pipeline logs provider"
            String script = """
                    node {
                        withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                            ttProvideLogs(publishConfig: [allowMissing: true])
                        }
                    }
                    """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etLogsFolderName to jenkins.", run)
            jenkins.assertLogContains("Providing all $etLogsFolderName...", run)
            jenkins.assertLogContains("No files found to archive!", run)
    }

    def "ttProvideLogs: Test for failOnError false"() {
        given: "a pipeline logs provider"
            String script = """
                    node {
                        withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                            ttProvideLogs(publishConfig: [allowMissing: true, failOnError: false], reportIds: ['1'])
                        }
                    }
                    """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.UNSTABLE, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etLogsFolderName to jenkins.", run)
            jenkins.assertLogContains("[WARNING] Report with id 1 could not be found!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("Build result set to ${Result.UNSTABLE.toString()} due to warnings.", run)
    }

    def "ttRunPackage: keep current configuration."() {
        given: "a pipeline with predefined testConfig paths"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                         httpRequest(
                            ignoreSslErrors: true,
                            responseHandle: 'NONE',
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration",
                            wrapAsMultipart: false,
                            customHeaders: [[name: 'Content-Type', value: 'application/json']],
                            httpMode: 'PUT',
                            requestBody: '''{
                                "action": "Start",
                                "tbc": { "tbcPath": "test.tbc" },
                                "tcf": { "tcfPath": "test.tcf" }
                            }'''
                        )
    
                        ttRunPackage testCasePath: 'test.pkg'
    
                        def response = httpRequest(
                            ignoreSslErrors: true,
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration"
                        )
                        echo response.content
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about predefined paths being kept"
            jenkins.assertLogContains("Response Code: 200", run)
            jenkins.assertLogContains("Executing package 'test.pkg'...", run)
            jenkins.assertLogContains("\"tbc\": {\"tbcPath\": \"test.tbc\"}, \"tcf\": {\"tcfPath\": \"test.tcf\"}", run)
            jenkins.assertLogContains("\"action\": \"Start\"", run)
    }

    def "ttRunPackage: unload current configuration"() {
        given: "a pipeline with predefined testConfig paths"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                         httpRequest(
                            ignoreSslErrors: true,
                            responseHandle: 'NONE',
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration",
                            wrapAsMultipart: false,
                            customHeaders: [[name: 'Content-Type', value: 'application/json']],
                            httpMode: 'PUT',
                            requestBody: '''{
                                "action": "Start",
                                "tbc": { "tbcPath": "test.tbc" },
                                "tcf": { "tcfPath": "test.tcf" }
                            }'''
                        )
    
                        ttRunPackage testCasePath: 'test.pkg', testConfig: [tbcPath: '', tcfPath: '']
    
                        def response = httpRequest(
                            ignoreSslErrors: true,
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration"
                        )
                        echo response.content
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about predefined paths being empty"
            jenkins.assertLogContains("Response Code: 200", run)
            jenkins.assertLogContains("Executing package 'test.pkg'...", run)
            jenkins.assertLogContains("\"tbc\": {\"tbcPath\": \"\"}, \"tcf\": {\"tcfPath\": \"\"}", run)
    }

    def "ttRunPackage: force test configuration reload"() {
        given: "a pipeline with predefined testConfig paths"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                         httpRequest(
                            ignoreSslErrors: true,
                            responseHandle: 'NONE',
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration",
                            wrapAsMultipart: false,
                            customHeaders: [[name: 'Content-Type', value: 'application/json']],
                            httpMode: 'PUT',
                            requestBody: '''{
                                "action": "Start",
                                "tbc": { "tbcPath": "test.tbc" },
                                "tcf": { "tcfPath": "test.tcf" }
                            }'''
                        )
    
                        ttRunPackage testCasePath: 'test.pkg', 
                            testConfig: [tbcPath: 'test.tbc', tcfPath: 'test.tcf', forceConfigurationReload: true]                          
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        and:
            String containerLogs = etContainer.getLogs()
        then: "expect log information about predefined paths being empty"
            jenkins.assertLogContains("Response Code: 200", run)
            jenkins.assertLogContains("Executing package 'test.pkg'...", run)
            containerLogs.contains("Stop TCF")
            containerLogs.contains("Stop TBC")
            containerLogs.contains("Load TCF")
            containerLogs.contains("Load TBC")
            containerLogs.contains("Start TCF")
            containerLogs.contains("Start TBC")
    }

    def "ttProvideLogs: Test happy path"() {
        given: "a pipeline with test packages and log provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'test.pkg'
                        ttProvideLogs()
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etLogsFolderName to jenkins.", run)
            jenkins.assertLogContains("Successfully added $etLogsFolderName to jenkins.", run)
    }

    def "ttProvideReports: Test for missing files"() {
        given: "a pipeline reports provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttProvideReports()
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)
        then: "expect log information about failed pipeline run"
            jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("Providing all $etReportsFolderName...", run)
            jenkins.assertLogContains("Providing $etReportsFolderName failed!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing $etReportsFolderName. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
    }

    def "ttProvideReports: Test for failOnError false"() {
        given: "a pipeline logs provider"
            String script = """
                        node {
                            withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                                ttProvideReports(publishConfig: [allowMissing: true, failOnError: false], reportIds: ['1'])
                            }
                        }
                        """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.UNSTABLE, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("[WARNING] Report with id 1 could not be found!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("Build result set to ${Result.UNSTABLE.toString()} due to warnings.", run)
    }

    def "ttProvideReports: Test for allowed missing files"() {
        given: "a pipeline reports provider"
            String script = """
                    node {
                        withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                            ttProvideReports(publishConfig: [allowMissing: true])
                        }
                    }
                    """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
    }

    def "ttProvideReports: test happy path"() {
        given: "a pipeline with test packages and report provider"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    ttProvideReports()
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("Successfully added $etReportsFolderName to jenkins.", run)
    }

    def "ttProvideGeneratedReports: missing files"() {
        given: "a pipeline reports provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttProvideGeneratedReports()
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)
        then: "expect log information about failed pipeline run"
            jenkins.assertLogContains("Providing $etGeneratedReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("Providing all $etGeneratedReportsFolderName...", run)
            jenkins.assertLogContains("Providing $etGeneratedReportsFolderName failed!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("ERROR: Build result set to FAILURE due to missing $etGeneratedReportsFolderName. " +
                    "Adjust AllowMissing step property if this is not intended.", run)
    }

    def "ttProvideGeneratedReports: Test for failOnError false"() {
        given: "a pipeline logs provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttProvideGeneratedReports(publishConfig: [allowMissing: true, failOnError: false], reportIds: ['1'])
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.UNSTABLE, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etGeneratedReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("[WARNING] Report with id 1 could not be found!", run)
            jenkins.assertLogContains("No files found to archive!", run)
            jenkins.assertLogContains("Build result set to ${Result.UNSTABLE.toString()} due to warnings.", run)
    }

    def "ttProvideGeneratedReports: allow missing files"() {
        given: "a pipeline reports provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttProvideGeneratedReports(publishConfig: [allowMissing: true])
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etGeneratedReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("No files found to archive!", run)
    }

    def "ttProvideGeneratedReports: happy path"() {
        given: "a pipeline with test packages and report provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'test.pkg'
                        ttGenerateReports 'HTML'
                        ttProvideGeneratedReports()
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etGeneratedReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("Successfully added $etGeneratedReportsFolderName to jenkins.", run)
    }

    def "ttProvideGeneratedReports: no matching reports"() {
        given: "a pipeline with test packages and report provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'test.pkg'
                        ttGenerateReports 'HTML'
                        ttProvideGeneratedReports publishConfig: [allowMissing: true], selectedReportTypes: 'NOMATCH'
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect log information about successful pipeline run"
            jenkins.assertLogContains("Providing $etGeneratedReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("Could not find any matching generated report files", run)
            jenkins.assertLogContains("No files found to archive!", run)
    }

    def "ttProvideUnitReports: happy path"() {
        given: "a pipeline that provides the needed test report"
            // project has two tests, one successful and one failing
            // unit.tcf will auto create a standard and a custom unit test report (the default glob will match both reports)
            String prepareScript = toPipelineScript("ttRunProject testCasePath: 'ttProvideUnitReports/RunTests.prj', testConfig: [tbcPath: '', tcfPath: 'unit.tcf']")
            WorkflowJob prepareJob = jenkins.createProject(WorkflowJob.class, "prepare-test")
            prepareJob.setDefinition(new CpsFlowDefinition(prepareScript, true))
        and: "a pipeline that parses the data without thresholds"
            String successScript = toPipelineScript("ttProvideUnitReports()")
            WorkflowJob successJob = jenkins.createProject(WorkflowJob.class, "run-success")
            successJob.setDefinition(new CpsFlowDefinition(successScript, true))
        and: "a pipeline that evaluates a custom report glob"
            String customScript = toPipelineScript("ttProvideUnitReports reportGlob: 'MyUnitReport/junit-report.xml'")
            WorkflowJob customJob = jenkins.createProject(WorkflowJob.class, "run-custom")
            customJob.setDefinition(new CpsFlowDefinition(customScript, true))
        and: "a pipeline the has a unstableThreshold"
            String unstableScript = toPipelineScript("ttProvideUnitReports unstableThreshold: 20.0")
            WorkflowJob unstableJob = jenkins.createProject(WorkflowJob.class, "run-unstable")
            unstableJob.setDefinition(new CpsFlowDefinition(unstableScript, true))
        and: "a Pipeline the has a failed threshold"
            String failedScript = toPipelineScript("ttProvideUnitReports failedThreshold: 20.0")
            WorkflowJob failedJob = jenkins.createProject(WorkflowJob.class, "run-failed")
            failedJob.setDefinition(new CpsFlowDefinition(failedScript, true))

        when: "scheduling prepare job"
            WorkflowRun prepareRun = jenkins.buildAndAssertStatus(Result.SUCCESS, prepareJob)
        then: "expect project was executed correctly"
            jenkins.assertLogContains("Executing project 'ttProvideUnitReports/RunTests.prj'", prepareRun)
            jenkins.assertLogContains("Project executed successfully.", prepareRun)

        when: "scheduling successful running jobs"
            WorkflowRun successRun = jenkins.buildAndAssertStatus(Result.SUCCESS, successJob)
            WorkflowRun customRun = jenkins.buildAndAssertStatus(Result.SUCCESS, customJob)
        then: "expect they are successful and contain test results"
            jenkins.assertLogContains("Found 4 test result(s) in total: #Passed: 2, #Failed: 2, #Skipped: 0", successRun)
            jenkins.assertLogContains("Successfully added test results to Jenkins.", successRun)
            successRun.getAction(TestResultAction.class).getTotalCount() == 4
            jenkins.assertLogContains("Found 2 test result(s) in total: #Passed: 1, #Failed: 1, #Skipped: 0", customRun)
            jenkins.assertLogContains("Successfully added test results to Jenkins.", customRun)
            customRun.getAction(TestResultAction.class).getTotalCount() == 2

        when: "scheduling non-successful jobs"
            WorkflowRun unstableRun = jenkins.buildAndAssertStatus(Result.UNSTABLE, unstableJob)
            WorkflowRun failedRun = jenkins.buildAndAssertStatus(Result.FAILURE, failedJob)
        then: "they run as expected, log the reaching of the threshold and contain test results"
            jenkins.assertLogContains("Found 4 test result(s) in total: #Passed: 2, #Failed: 2, #Skipped: 0", unstableRun)
            jenkins.assertLogContains("Successfully added test results to Jenkins.", unstableRun)
            jenkins.assertLogContains("Build result set to UNSTABLE due to percentage of failed tests is higher than the configured threshold", unstableRun)
            unstableRun.getAction(TestResultAction.class).getTotalCount() == 4
            jenkins.assertLogContains("Found 4 test result(s) in total: #Passed: 2, #Failed: 2, #Skipped: 0", failedRun)
            jenkins.assertLogContains("Successfully added test results to Jenkins.", failedRun)
            jenkins.assertLogContains("Build result set to FAILURE due to percentage of failed tests is higher than the configured threshold", failedRun)
            failedRun.getAction(TestResultAction.class).getTotalCount() == 4
    }

    def "ttLoadConfig: happy path"() {
        given: "a pipeline that loads a test configuration"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttLoadConfig tbcPath: 'test.tbc', tcfPath: 'test.tcf'

                        def response = httpRequest(
                            ignoreSslErrors: true,
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration"
                        )
                        echo response.content
                    }
                }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-ttLoadConfig")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "the configuration is set and the build is successful"
            jenkins.assertLogContains("Response Code: 200", run)
            jenkins.assertLogContains("\"action\": \"Start\"", run)
            jenkins.assertLogContains("\"tbc\": {\"tbcPath\": \"test.tbc\"}", run)
            jenkins.assertLogContains("\"tcf\": {\"tcfPath\": \"test.tcf\"}", run)
            jenkins.assertLogContains("{\"message\": \"Configuration successfully started.\", \"key\": \"FINISHED\"}", run)
            logRun(run)
    }

    def "ttLoadConfig: load only without start"() {
        given: "a pipeline that loads test configuration without starting it"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttLoadConfig tbcPath: 'test.tbc', tcfPath: 'test.tcf', startConfig: false

                        def response = httpRequest(
                            ignoreSslErrors: true,
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration"
                        )
                        echo response.content
                    }
                }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-ttLoadConfig-noStart")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "the configuration is loaded but not started and the build is successful"
        jenkins.assertLogContains("Response Code: 200", run)
        jenkins.assertLogContains("\"action\": \"Load\"", run)
        jenkins.assertLogContains("\"tbc\": {\"tbcPath\": \"test.tbc\"}", run)
        jenkins.assertLogContains("\"tcf\": {\"tcfPath\": \"test.tcf\"}", run)
        jenkins.assertLogContains("{\"message\": \"Configuration successfully loaded.\", \"key\": \"FINISHED\"}", run)
        logRun(run)
    }

    def "ttLoadConfig: unload previous configuration"() {
        given: "a pipeline that loads a configuration and then unloads it by loading empty paths"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        // initial load
                        ttLoadConfig tbcPath: 'test.tbc', tcfPath: 'test.tcf'
                        // unload by loading empty paths
                        ttLoadConfig tbcPath: '', tcfPath: ''

                        def response = httpRequest(
                            ignoreSslErrors: true,
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration"
                        )
                        echo response.content
                    }
                }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-ttLoadConfig-unload")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "the first configuration is loaded then cleared on second load"
            // final API response contains empty paths
            jenkins.assertLogContains("Response Code: 200", run)
            jenkins.assertLogContains("\"action\": \"Start\"", run)
            jenkins.assertLogContains("\"tbc\": {\"tbcPath\": \"\"}", run)
            jenkins.assertLogContains("\"tcf\": {\"tcfPath\": \"\"}", run)
            jenkins.assertLogContains("{\"message\": \"Configuration successfully started.\", \"key\": \"FINISHED\"}", run)
            logRun(run)
    }

    def "ttLoadConfig: load with constants"() {
        given: "a pipeline that loads a configuration with two constants"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttLoadConfig tbcPath: 'test.tbc', tcfPath: 'test.tcf', constants: [[label: 'constString', value: '\"constValue\"'], [label: 'constInt', value: '123'], [label: 'constBool', value: 'True']]

                        def response = httpRequest(
                            ignoreSslErrors: true,
                            url: "http://\${ET_API_HOSTNAME}:\${ET_API_PORT}/api/v2/configuration"
                        )
                        echo response.content
                    }
                }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-ttLoadConfig-constants")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "the configuration is started and constants are passed"
            jenkins.assertLogContains("Response Code: 200", run)
            jenkins.assertLogContains("\"action\": \"Start\"", run)
            jenkins.assertLogContains("\"tbc\": {\"tbcPath\": \"test.tbc\"}", run)
            jenkins.assertLogContains("\"tcf\": {\"tcfPath\": \"test.tcf\"}", run)
            jenkins.assertLogContains("\"constants\": [{\"label\": \"constString\", \"value\": \"\\\"constValue\\\"\"}, {\"label\": \"constInt\", \"value\": \"123\"}, {\"label\": \"constBool\", \"value\": \"True\"}]", run)
            jenkins.assertLogContains("{\"message\": \"Configuration successfully started.\", \"key\": \"FINISHED\"}", run)
            logRun(run)
    }

    def "ttLoadConfig: fails on non-existing TCF"() {
        given: "a pipeline that attempts to load a non-existing TCF"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        // try to load a TCF that does not exist
                        ttLoadConfig tbcPath: 'test.tbc', tcfPath: 'does-not-exist.tcf'
                    }
                }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-ttLoadConfig-missing-tcf")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)

        then: "the build fails and logs the configuration loading error"
            jenkins.assertLogContains("Loading configuration failed!", run)
            logRun(run)
    }

    def "ttLoadConfig: fails on non-existing TBC"() {
        given: "a pipeline that attempts to load a non-existing TBC"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        // try to load a TBC that does not exist
                        ttLoadConfig tbcPath: 'does-not-exist.tbc', tcfPath: 'test.tcf'
                    }
                }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-ttLoadConfig-missing-tbc")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)

        then: "the build fails and logs the configuration loading error"
            jenkins.assertLogContains("Loading configuration failed!", run)
            logRun(run)
    }

    private void logRun(WorkflowRun run) {
        String log = jenkins.getLog(run)
        println("===== Pipeline Log Start =====")
        println(log)
        println("===== Pipeline Log End =====")
    }

}
