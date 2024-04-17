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
import org.junit.Rule
import org.jvnet.hudson.test.GroovyJenkinsRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils

import static org.hamcrest.CoreMatchers.containsStringIgnoringCase
import static org.hamcrest.MatcherAssert.assertThat

abstract class ETContainerTest extends ContainerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ETContainerTest.class)

    @Rule
    protected GroovyJenkinsRule jenkins = new GroovyJenkinsRule()

    protected GenericContainer etContainer = getETContainer()

    abstract GenericContainer getETContainer()

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
            jenkins.assertLogContains("Executing Package Checks for: test.pkg", run)
            jenkins.assertLogContains("-> result: SUCCESS", run)
    }

    def "Perform check step on non-existing package"() {
        given: "a test execution pipeline"
            String script = """
                    node {
                        withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                            ttCheckPackage testCasePath: 'testDoesNotExist.pkg'
                        }
                    }
                    """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect error"
            jenkins.assertLogContains("Executing Package Checks failed!", run)
            jenkins.assertLogContains("-> result: ERROR", run)
            // ecu.test 2024.1 and newer returns case sensitive messages
            assertThat(jenkins.getLog(run), containsStringIgnoringCase("BAD REQUEST"))
    }

    def "Perform check on invalid package"() {
        given: "a test execution pipeline"
            String script = """
                        node {
                            withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                                ttCheckPackage testCasePath: 'invalid_package_desc.pkg'
                            }
                        }
                        """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect error"
            jenkins.assertLogContains("Executing Package Checks for: invalid_package_desc.pkg", run)
            jenkins.assertLogContains("-> result: ERROR", run)
            jenkins.assertLogContains("--> invalid_package_desc.pkg:  Description must not be empty!", run)
    }

    def "Perform check with timeout"() {
        given: "a test execution pipeline"
            int timeout = 1
            String testPkg = 'invalid_package_desc.pkg'
            String script = """
                            node {
                                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                                    ttCheckPackage executionConfig: [stopOnError: false, stopUndefinedTools: false, timeout: ${timeout}], testCasePath: '${testPkg}'
                                }
                            }
                            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

            when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)

        then: "expect error"
            jenkins.assertLogContains("Executing Package Checks for: invalid_package_desc.pkg", run)
            jenkins.assertLogContains("Executing Package Checks failed!", run)
            jenkins.assertLogContains("Timeout: step execution took longer than ${timeout} seconds", run)
            jenkins.assertLogContains("-> result: ERROR", run)
    }

    def "Perform check on project"() {
        given: "a test execution pipeline"
            String script = """
                            node {
                                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                                    ttCheckPackage testCasePath: 'test.prj'
                                }
                            }
                            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test completion"
            jenkins.assertLogContains("Executing Package Checks for: test.prj", run)
            jenkins.assertLogContains("-> result: SUCCESS", run)
    }

    def "Perform check on project with invalid packages"() {
        given: "a test execution pipeline"
            String script = """
                        node {
                            withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                                ttCheckPackage testCasePath: 'invalid_package_desc.prj'
                            }
                        }
                        """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))
        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect error"
            jenkins.assertLogContains("Executing Package Checks for: invalid_package_desc.prj", run)
            jenkins.assertLogContains("-> result: ERROR", run)
            jenkins.assertLogContains("--> invalid_package_desc.pkg:  Description must not be empty!", run)
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
            jenkins.assertLogContains("-> result: SUCCESS", run)
            jenkins.assertLogContains("-> reportDir: ${ET_WS_PATH}/TestReports/test_", run)
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

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect error"
            jenkins.assertLogContains("-> result: ERROR", run)
            jenkins.assertLogContains("Executing package testDoesNotExist.pkg failed!", run)
    }

    def "Execute test case including package check"() {
        given: "a test execution pipeline"
            String script = """
                node {
                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                        ttRunPackage testCasePath: 'invalid_package_desc.pkg', 
                            executionConfig: [executePackageCheck: true, stopOnError: false],
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
            jenkins.assertLogContains("Executing Package Checks for: invalid_package_desc.pkg", run)
            jenkins.assertLogContains("-> result: ERROR", run)
            jenkins.assertLogContains("--> invalid_package_desc.pkg:  Description must not be empty!", run)
            jenkins.assertLogContains("Executing package invalid_package_desc.pkg", run)
            jenkins.assertLogContains("-> result: SUCCESS", run)
            jenkins.assertLogContains("-> reportDir: ${ET_WS_PATH}/TestReports/invalid_package_desc_", run)
    }

    def "Execute package with timeout"() {
        given: "a test execution pipeline"
            int timeout = 1
            String testPkg = 'test.pkg'
            String script = """
                                node {
                                    withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                                        ttRunPackage executionConfig: [stopOnError: false, stopUndefinedTools: false, timeout: ${timeout}], testCasePath: '${testPkg}'
                                    }
                                }
                                """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.FAILURE, job)

        then: "expect error"
            jenkins.assertLogContains("Executing package ${testPkg}", run)
            jenkins.assertLogContains("Executing package ${testPkg} failed!", run)
            jenkins.assertLogContains("Timeout: step execution took longer than ${timeout} seconds", run)
        }

    def "Generate report format"() {
        given: "a test execution and report generation pipeline"
            String script = """
            node {
                withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) {
                    ttRunPackage testCasePath: 'test.pkg'
                    ttRunProject testCasePath: 'test.prj'
                    def generationReports = ttGenerateReports generatorName: 'HTML', additionalSettings: [[name: 'javascript', value: 'False']]
                    echo "\${generationReports.reportOutputDir}"
                    echo "size of returned array: \${generationReports.size()}"
                }
            }
            """.stripIndent()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline")
            job.setDefinition(new CpsFlowDefinition(script, true))

        when: "scheduling a new build"
            WorkflowRun run = jenkins.buildAndAssertStatus(Result.SUCCESS, job)

        then: "expect successful test and upload completion"
            StringUtils.countMatches(jenkins.getLog(run), "Generating HTML report format for report id") == 2
            StringUtils.countMatches(jenkins.getLog(run), "-> FINISHED") == 2
            StringUtils.contains(jenkins.getLog(run), "size of returned array: 2")
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
            jenkins.assertLogContains("-> FINISHED", run)
    }
}
