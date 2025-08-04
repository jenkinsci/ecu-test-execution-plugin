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
    private static final String TG_IMAGE_NAME =  System.getenv('REGISTRY_GITLAB') + "/hausintern/productdemos/docker/docker-base-images/test-guide:"+ System.getenv('TG_VERSION')
    private static final String TG_AUTH_KEY = System.getenv('TG_AUTH_KEY')
    private static final String TG_ALIAS = 'tgTestContainer'

    @Shared
    private Network network = Network.newNetwork()

    @Shared
    private GenericContainer tgContainer = new GenericContainer<>(TG_IMAGE_NAME)
            .withExposedPorts(TG_PORT)
            .withNetwork(network)
            .withNetworkAliases(TG_ALIAS)
            .withClasspathResourceMapping("autoConfig.prop", "/var/testguide/TTS-TM/autoConfig.prop", BindMode.READ_ONLY)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forHttp("/api/health/ready").withStartupTimeout(Duration.ofMinutes(15)))

    private GenericContainer etContainer = new GenericContainer<>(ET_V2_IMAGE_NAME)
            .withExposedPorts(ET_PORT)
            .withNetwork(network)
            .withEnv("tracet_LICENSE", ET_LICENSE_SERVER)
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

    def "ttUploadReports: happy path"() {
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
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect successful test and upload completion"
            StringUtils.countMatches(jenkins.getLog(run), "result: SUCCESS") == 2
            StringUtils.contains(jenkins.getLog(run), "size of returned array: 2")
    }

    def "ttUploadReports: specific test report"() {
        given: "a test execution and upload pipeline"
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

    def "ttUploadReports: invalid test report"() {
        given: "a test execution and upload pipeline"
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

    def "ttUploadReports: test report with invalid config"() {
        given: "a test execution and upload pipeline"
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
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)
        then: "expect successful test but upload failed"
            jenkins.assertLogContains("Uploading ATX report for report id", run)
            jenkins.assertLogContains("Build result set to FAILURE due to failed report upload. " +
                    "Set Pipeline step property 'Fail On Error' to 'false' to ignore failed report uploads.", run)
    }

    def "ttUploadReports: test report with invalid config and failOnError false"() {
        given: "a test execution and upload pipeline"
            String script = """
                    node {
                        withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                            ttRunPackage testCasePath: 'test.pkg'
                            ttRunProject testCasePath: 'test.prj'
                            ttUploadReports testGuideUrl: 'http://${TG_ALIAS}:${TG_PORT}',
                                credentialsId: 'authKey', failOnError: false
                        }
                    }
                    """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then: "expect successful test but upload failed"
            StringUtils.countMatches(jenkins.getLog(run), "Uploading ATX report for report id") == 2
            StringUtils.countMatches(jenkins.getLog(run), "failed") == 2
            jenkins.assertLogContains("Report upload(s) unstable. Please see the logging of the uploads.", run)
    }

    def "ttUploadReports: Test #scenario upload using additionalSettings and verify with test.guide request"() {
        given:
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}','TG_HOSTNAME=${tgContainer.host}', 'TG_API_PORT=${tgContainer.getMappedPort(TG_PORT)}']) {
                        def run_res = ttRunPackage 'test.pkg'
    
                        def upload_res = ttUploadReports testGuideUrl: 'http://${TG_ALIAS}:${TG_PORT}',
                            credentialsId: 'authKey', 
                            useSettingsFromServer: false,
                            reportIds: [run_res.getReportId()],
                            failOnError: false, 
                            additionalSettings: [
                                [name: "uploadAsync", value: "${uploadAsync}"],
                                [name: "setConstants", value: "${customConstants}"],
                                [name: "setAttributes", value: "${customAttributes}"]
                            ]     
                            
                        sleep(2)
                                                    
                        def response = httpRequest (
                                            ignoreSslErrors: true,
                                            acceptType: 'APPLICATION_JSON',
                                            contentType: 'APPLICATION_JSON',
                                            httpMode: 'POST',
                                            url: "http://\${TG_HOSTNAME}:\${TG_API_PORT}/api/report/testCaseExecutions/filter?projectId=1&offset=0&limit=10",
                                            customHeaders: [
                                                [name: 'TestGuide-AuthKey', value: "${TG_AUTH_KEY}"]
                                            ],
                                            requestBody: '''
                                            {
                                                "testCaseName": ["test"],
                                                "attributes": [
                                                    ${expectAttribues}
                                                ],
                                                "constants": [
                                                    ${expectConstants}
                                                ]
                                            }
                                            '''
                                       )
                        if (response.status == 200 && response.content != []) {
                            println "Successfully retrieved the report from test.guide"
                            println response.content

                        } else {
                            println "Retrieving the report from test.guide failed"
                        }
                        
                    }
                }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when:
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)
        then:
            //jenkins.assertLogContains("Uploaded successfully", run) TODO ecu.test does not return a report link for uploadAsync:True even tho the report is present in test.guide
            jenkins.assertLogContains("Successfully retrieved the report from test.guide", run)
            jenkins.assertLogContains('"testCaseName":"test"', run)
            jenkins.assertLogContains(expectAttribues, run)
            jenkins.assertLogContains(expectConstants, run)
        where:
            scenario                | uploadAsync   | customAttributes              | customConstants               |expectAttribues                                      | expectConstants
            "synchronous"           | "False"       | "CustomAttribute=${scenario}" | "CustomConstant=${scenario}"  |'{"key":"CustomAttribute","values":["'+scenario+'"]}'|'{"key":"CustomConstant","values":["'+scenario+'"]}'
            "asynchronous"          | "True"        | "CustomAttribute=${scenario}" | "CustomConstant=${scenario}"  |'{"key":"CustomAttribute","values":["'+scenario+'"]}'|'{"key":"CustomConstant","values":["'+scenario+'"]}'
    }
}
