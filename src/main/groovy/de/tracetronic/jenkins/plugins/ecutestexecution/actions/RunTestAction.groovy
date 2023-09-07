/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.actions

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import hudson.model.InvisibleAction

abstract class RunTestAction extends InvisibleAction {

    private final String testCasePath
    private final TestConfig testConfig
    private final TestResult testResult

    protected RunTestAction(String testCasePath, TestConfig testConfig, TestResult testResult) {
        this.testCasePath = testCasePath
        this.testConfig = testConfig
        this.testResult = testResult
    }

    String getTestCasePath() {
        return testCasePath
    }

    TestConfig getTestConfig() {
        return new TestConfig(testConfig)
    }

    TestResult getTestResult() {
        return new TestResult(testResult)
    }
}
