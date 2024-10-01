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
    private static final String etLogsFolderName = 'ecu.test-logs'
    private static final String etReportsFolderName = 'ecu.test-reports'

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

    def "Perform provide logs step with no logs"() {
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
            jenkins.assertLogContains("[WARNING] No files found!", run)
            jenkins.assertLogContains("ERROR: Build Result set to FAILURE due to missing $etLogsFolderName. Adjust AllowMissing step property if this is not intended.", run)
    }

    def "Perform provide logs step allow missing"() {
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
            jenkins.assertLogContains("[WARNING] No files found!", run)
    }

    def "Perform provide logs step with logs"() {
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

    def "Perform provide reports step with no reports"() {
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
            jenkins.assertLogContains("[WARNING] No files found!", run)
            jenkins.assertLogContains("ERROR: Build Result set to FAILURE due to missing $etReportsFolderName. Adjust AllowMissing step property if this is not intended.", run)
    }

    def "Perform provide reports step allow missing"() {
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
            jenkins.assertLogContains("[WARNING] No files found!", run)
    }

    def "Perform provide reports step with reports"() {
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

    def "Perform provide generated reports step with no reports"() {
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
            jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("[WARNING] No files found!", run)
            jenkins.assertLogContains("ERROR: Build Result set to FAILURE due to missing $etReportsFolderName. Adjust AllowMissing step property if this is not intended.", run)
    }

    def "Perform provide generated reports step allow missing"() {
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
            jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("[WARNING] No files found!", run)
    }

    def "Perform provide generated reports step with reports"() {
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
            jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
            jenkins.assertLogContains("Successfully added $etReportsFolderName to jenkins.", run)
    }

    def "Perform provide generated reports step with excluded reports"() {
            given: "a pipeline with test packages and report provider"
                String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'test.pkg'
                        ttGenerateReports 'HTML'
                        ttProvideGeneratedReports("ATX*/**")
                    }
                }
                """.stripIndent()
                WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
                job.setDefinition(new CpsFlowDefinition(script, true))
            when: "scheduling a new build"
                WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

            then: "expect log information about successful pipeline run"
                jenkins.assertLogContains("Providing $etReportsFolderName to jenkins.", run)
                jenkins.assertLogContains("[WARNING] Could not find any matching generated report files", run)
                jenkins.assertLogContains("[WARNING] No files found!", run)
        }
}
