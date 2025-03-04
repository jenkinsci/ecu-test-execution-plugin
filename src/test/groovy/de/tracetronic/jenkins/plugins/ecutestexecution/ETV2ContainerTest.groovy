/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.model.Result
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
            jenkins.assertLogContains("Response Code: HTTP/1.1 200 OK", run)
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
            jenkins.assertLogContains("Response Code: HTTP/1.1 200 OK", run)
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
            jenkins.assertLogContains("Response Code: HTTP/1.1 200 OK", run)
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
}
