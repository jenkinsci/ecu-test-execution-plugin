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

    def "Perform provide logs step with no reports"() {
            given: "a test execution pipeline"
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
                WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

            then: "expect successful test completion"
                jenkins.assertLogContains("Providing ecu.test logs to jenkins.", run)
                jenkins.assertLogContains("[WARNING] No ecu.test log files found!", run)
        }

    def "Perform provide logs step with reports"() {
        given: "a test execution pipeline"
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

        then: "expect successful test completion"
            jenkins.assertLogContains("Providing ecu.test logs to jenkins.", run)
            jenkins.assertLogContains("Successfully added ecu.test logs to jenkins.", run)
    }
}
