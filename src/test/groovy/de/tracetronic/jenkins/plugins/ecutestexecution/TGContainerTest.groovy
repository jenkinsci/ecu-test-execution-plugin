/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import hudson.model.Result
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
import org.testcontainers.shaded.org.apache.commons.lang.StringUtils
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared

@Testcontainers
class TGContainerTest extends ContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TGContainerTest.class)

    private static final int TG_PORT = 8085
    private static final String TG_IMAGE_NAME = BASE_IMAGE_PATH + "test-guide:" + System.getenv('TG_VERSION')
    private static final String TG_AUTH_KEY = System.getenv('TG_AUTH_KEY')

    private Network network = Network.newNetwork()

    @Shared
    private GenericContainer tgContainer = new GenericContainer<>(TG_IMAGE_NAME)
            .withExposedPorts(TG_PORT)
            .withNetwork(network)
            .withClasspathResourceMapping("autoConfig.prop", "/app/TTS-TM/autoConfig.prop", BindMode.READ_ONLY)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forHttp("/api/health/live"))

    private GenericContainer etContainer = new GenericContainer<>(ET_IMAGE_NAME)
            .withExposedPorts(ET_PORT)
            .withNetwork(network)
            .withClasspathResourceMapping("workspace", "/app/workspace", BindMode.READ_ONLY)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forHttp("/api/v1/live"))
            .dependsOn(tgContainer)

    @Rule
    private GroovyJenkinsRule jenkins = new GroovyJenkinsRule()

    def setup() {
        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, 'authKey', 'TEST-GUIDE auth key', '', TG_AUTH_KEY))
    }

    def "Upload test reports"() {
        given: "a test execution and upload pipeline"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    ttRunProject testCasePath: 'test.prj'
                    ttUploadReports testGuideUrl: 'http://${tgContainer.host}:${tgContainer.getMappedPort(TG_PORT)}',
                        credentialsId: 'authKey'
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test and upload completion"
            StringUtils.countMatches(jenkins.getLog(run), "result: FINISHED") == 2
    }

    def "Upload a specific test report"() {
        given: "a test execution and upload pipeline"
            Secret authKey = Secret.fromString(TG_AUTH_KEY)
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    def result = ttRunProject testCasePath: 'test.prj'
                    ttUploadReports testGuideUrl: 'http://${tgContainer.host}:${tgContainer.getMappedPort(TG_PORT)}',
                        credentialsId: 'authKey', reportIds: [result.reportId]
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test and upload completion"
            StringUtils.countMatches(jenkins.getLog(run), "result: FINISHED") == 1
    }
}
