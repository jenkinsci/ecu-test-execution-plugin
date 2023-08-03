/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Rule
import org.jvnet.hudson.test.GroovyJenkinsRule
import org.jvnet.hudson.test.JenkinsRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils
import org.testcontainers.spock.Testcontainers

@Testcontainers
class ETContainerTest extends ContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ETContainerTest.class)

    private GenericContainer etContainer = new GenericContainer<>(ET_IMAGE_NAME)
            .withExposedPorts(ET_PORT)
            .withClasspathResourceMapping("workspace/.workspace", "${ET_WS_PATH}/.workspace",
                    BindMode.READ_ONLY)
            .withClasspathResourceMapping("workspace/Configurations",
                    "${ET_WS_PATH}/Configurations", BindMode.READ_ONLY)
            .withClasspathResourceMapping("workspace/Packages", "${ET_WS_PATH}/Packages",
                    BindMode.READ_ONLY)
            .withClasspathResourceMapping("workspace/localsettings.xml", "${ET_WS_PATH}/localsettings.xml",
                    BindMode.READ_ONLY)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forHttp("/api/v1/live"))

    @Rule
    private GroovyJenkinsRule jenkins = new GroovyJenkinsRule()

    def "Perform check step"() {
        given: "a test execution pipeline"
        String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttCheckPackage testCasePath: 'test.pkg'
                }
            }
            """.stripIndent()
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
        job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
        WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test completion"
        jenkins.assertLogContains("Found : 0 issues",run)
    }

    def "Execute test case"() {
        given: "a test execution pipeline"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg', 
                        testConfig: [tbcPath: 'test.tbc', 
                                     tcfPath: 'test.tcf', 
                                     forceConfigurationReload: false, 
                                     constants: [[label: 'test', value: '123']]]
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test completion"
            jenkins.assertLogContains("result: SUCCESS", run)
            jenkins.assertLogContains("reportDir: ${ET_WS_PATH}/TestReports/test_", run)
    }

    def "Execute nonexisting test case"() {
        given: "a test execution pipeline"
        String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'testDoesNotExist.pkg', 
                        testConfig: [tbcPath: 'test.tbc', 
                                     tcfPath: 'test.tcf', 
                                     forceConfigurationReload: false, 
                                     constants: [[label: 'test', value: '123']]]
                }
            }
            """.stripIndent()
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
        job.setDefinition(new CpsFlowDefinition(script, true))
        jenkins.jenkins.getDescriptorByType(ETInstallation.DescriptorImpl.class)
                .setInstallations(new ETInstallation('ECU-TEST',
                        '/bin/ecu-test', JenkinsRule.NO_PROPERTIES))

        when: "scheduling a new build"
        WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect error"
        jenkins.assertLogContains("result: ERROR", run)
        jenkins.assertLogContains("Executing package failed!", run)
        jenkins.assertLogContains("-> Tools stopped successfully.", run)
    }

    def "Generate report format"() {
        given: "a test execution and report generation pipeline"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    ttRunProject testCasePath: 'test.prj'
                    ttGenerateReports generatorName: 'HTML', additionalSettings: [[name: 'javascript', value: 'False']]
               }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test and upload completion"
            StringUtils.countMatches(jenkins.getLog(run), "result: FINISHED") == 2
            StringUtils.countMatches(jenkins.getLog(run), "reportOutputDir: ${ET_WS_PATH}/TestReports/test_") == 2
    }

    def "Generate report format for a specific test report"() {
        given: "a test execution and report generation pipeline"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    def result = ttRunProject testCasePath: 'test.prj'
                    ttGenerateReports generatorName: 'HTML', reportIds: [result.reportId]
               }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test and upload completion"
            StringUtils.countMatches(jenkins.getLog(run), "result: FINISHED") == 1
            StringUtils.countMatches(jenkins.getLog(run), "reportOutputDir: ${ET_WS_PATH}/TestReports/test_") == 1
    }
}
