/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import hudson.model.Result
import hudson.model.TaskListener
import hudson.util.Secret
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Rule
import org.jvnet.hudson.test.GroovyJenkinsRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared

import java.time.Duration

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase
import static org.hamcrest.MatcherAssert.assertThat

@Testcontainers
class TGContainerTest extends ContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TGContainerTest.class)

    private static final int TG_PORT = 8085
    private static final String TG_IMAGE_NAME = BASE_IMAGE_PATH + "test-guide:" + System.getenv('TG_VERSION')
    private static final String TG_AUTH_KEY = System.getenv('TG_AUTH_KEY')
    private static final String TG_ALIAS = 'tgTestContainer'

    @Shared
    private Network network = Network.newNetwork()

    @Shared
    private GenericContainer tgContainer = new GenericContainer<>(TG_IMAGE_NAME)
            .withExposedPorts(TG_PORT)
            .withNetwork(network)
            .withNetworkAliases(TG_ALIAS)
            .withClasspathResourceMapping("autoConfig.prop", "/app/TTS-TM/autoConfig.prop", BindMode.READ_ONLY)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forHttp("/api/health/ready").withStartupTimeout(Duration.ofMinutes(15)))

    private GenericContainer etContainer = new GenericContainer<>(ET_V2_IMAGE_NAME)
            .withExposedPorts(ET_PORT)
            .withNetwork(network)
            .withClasspathResourceMapping("workspace/.workspace", "${ET_WS_PATH}/.workspace",
                    BindMode.READ_ONLY)
            .withClasspathResourceMapping("workspace/Configurations",
                    "${ET_WS_PATH}/Configurations", BindMode.READ_ONLY)
            .withClasspathResourceMapping("workspace/Packages", "${ET_WS_PATH}/Packages",
                    BindMode.READ_ONLY)
            .withClasspathResourceMapping("workspace/localsettings.xml", "${ET_WS_PATH}/localsettings.xml",
                    BindMode.READ_ONLY)            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forHttp("/api/v2/live"))
            .dependsOn(tgContainer)

    @Rule
    private GroovyJenkinsRule jenkins = new GroovyJenkinsRule()

    def setup() {
        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, 'authKey', 'test.guide auth key', '', TG_AUTH_KEY))
    }

    def "Upload test reports"() {
        given: "a test execution and upload pipeline"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    ttRunProject testCasePath: 'test.prj'
                    def uploadReports = ttUploadReports testGuideUrl: 'http://${TG_ALIAS}:${TG_PORT}',
                        credentialsId: 'authKey', useSettingsFromServer: false
                    echo "size of returned array: \${uploadReports.size()}"
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
            TaskListener taskListener = jenkins.createTaskListener()

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test and upload completion"
            StringUtils.countMatches(jenkins.getLog(run), "result: SUCCESS") == 2
            StringUtils.contains(jenkins.getLog(run), "size of returned array: 2")
    }

    def "Upload a specific test report"() {
        given: "a test execution and upload pipeline"
            Secret authKey = Secret.fromString(TG_AUTH_KEY)
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    def result = ttRunProject testCasePath: 'test.prj'
                    ttUploadReports testGuideUrl: 'http://${TG_ALIAS}:${TG_PORT}',
                        credentialsId: 'authKey', reportIds: [result.reportId], 
                        useSettingsFromServer: false
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test and upload completion"
            jenkins.assertLogContains("-> Uploaded successfully", run)
            jenkins.assertLogContains("Report upload(s) successful", run)
    }

    def "Upload an invalid test report"() {
        given: "a test execution and upload pipeline"
            Secret authKey = Secret.fromString(TG_AUTH_KEY)
            String reportID = '0815-241543903-0815'
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'test.pkg'
                        ttRunProject testCasePath: 'test.prj'
                        ttUploadReports testGuideUrl: 'http://${TG_ALIAS}:${TG_PORT}',
                            credentialsId: 'authKey', reportIds: ['${reportID}'], 
                            useSettingsFromServer: false
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)

        then: "expect successful test but upload failed"
            jenkins.assertLogContains("404", run)
            jenkins.assertLogContains("no report with the given report ID ${reportID}", run)
            // ecu.test 2024.1 and newer returns case sensitive messages
            assertThat(jenkins.getLog(run), containsStringIgnoringCase("NOT FOUND"))
    }

    def "Upload test report invalid config"() {
        given: "a test execution and upload pipeline"
            Secret authKey = Secret.fromString('invalid_key')
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'test.pkg'
                        ttRunProject testCasePath: 'test.prj'
                        ttUploadReports testGuideUrl: 'http://${TG_ALIAS}:${TG_PORT}',
                            credentialsId: 'authKey'
                    }
                }
                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test but upload failed"
            StringUtils.countMatches(jenkins.getLog(run), "Report upload for") == 2
            StringUtils.countMatches(jenkins.getLog(run), "failed") == 2
            jenkins.assertLogContains(
                    "Report upload(s) unstable. Please see the logging of the uploads.", run)
    }
}
