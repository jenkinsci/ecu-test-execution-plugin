/*
 * Copyright (c) 2021-2025 tracetronic GmbH
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
class ETV1ContainerTest extends ETContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ETV1ContainerTest.class)

    GenericContainer getETContainer() {
        return new GenericContainer<>(ET_V1_IMAGE_NAME)
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
                .waitingFor(Wait.forHttp("/api/v1/live"))
    }

    def "ttProvideLogs: Test unsupported step"() {
        given: "a pipeline with test package and log provider"
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
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.UNSTABLE, job)

        then: "expect log information about unstable pipeline run"
            jenkins.assertLogContains("Providing ecu.test Logs to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Logs failed!", run)
            jenkins.assertLogContains("Downloading ecu.test Logs is not supported for this ecu.test version. Please use ecu.test >= 2024.2 instead.", run)
    }

    def "ttProvideReports: Test unsupported step"() {
        given: "a pipeline with test package and report provider"
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
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.UNSTABLE, job)

        then: "expect log information about unstable pipeline run"
            jenkins.assertLogContains("Providing ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing ecu.test Reports failed!", run)
            jenkins.assertLogContains("Downloading ecu.test Reports is not supported for this ecu.test version. Please use ecu.test >= 2024.3 instead.", run)
    }

    def "ttProvideGeneratedReports: Test unsupported step"() {
        given: "a pipeline with test package and report provider"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'test.pkg'
                        ttProvideGeneratedReports()
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.UNSTABLE, job)

        then: "expect log information about unstable pipeline run"
            jenkins.assertLogContains("Providing Generated ecu.test Reports to jenkins.", run)
            jenkins.assertLogContains("Providing Generated ecu.test Reports failed!", run)
            jenkins.assertLogContains("Downloading Generated ecu.test Reports is not supported for this ecu.test version. Please use ecu.test >= 2024.3 instead.", run)
    }

    def "ttProvideUnitReports: Test unsupported step"() {
        given: "a pipeline with test package and report provider"
            String script = """
                    node {
                        withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                            ttRunPackage testCasePath: 'test.pkg'
                            ttProvideUnitReports()
                        }
                    }
                    """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.UNSTABLE, job)

        then: "expect log information about unstable pipeline run"
            jenkins.assertLogContains("Providing Unit Reports to jenkins.", run)
            jenkins.assertLogContains("Providing Unit Reports failed!", run)
            jenkins.assertLogContains("Downloading Unit Reports is not supported for this ecu.test version. Please use ecu.test >= 2024.3 instead.", run)
    }
}
