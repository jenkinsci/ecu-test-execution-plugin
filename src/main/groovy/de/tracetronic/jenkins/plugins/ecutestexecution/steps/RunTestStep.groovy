/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull

abstract class RunTestStep extends Step {

    @Nonnull
    private final String testCasePath
    @Nonnull
    private TestConfig testConfig
    @Nonnull
    private ExecutionConfig executionConfig

    RunTestStep(String testCasePath) {
        super()
        this.testCasePath = StringUtils.trimToEmpty(testCasePath)
        this.testConfig = new TestConfig()
        this.executionConfig = new ExecutionConfig()
    }

    @Nonnull
    String getTestCasePath() {
        return testCasePath
    }

    @Nonnull
    TestConfig getTestConfig() {
        return new TestConfig(testConfig)
    }

    @DataBoundSetter
    void setTestConfig(TestConfig testConfig) {
        this.testConfig = testConfig ?: new TestConfig()
    }

    @Nonnull
    ExecutionConfig getExecutionConfig() {
        return new ExecutionConfig(executionConfig)
    }

    @DataBoundSetter
    void setExecutionConfig(ExecutionConfig executionConfig) {
        this.executionConfig = executionConfig ?: new ExecutionConfig()
    }
}
