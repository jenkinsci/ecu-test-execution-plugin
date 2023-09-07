/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.model

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

class TestResult implements Serializable {

    private static final long serialVersionUID = 1L

    private final String reportId
    private final String testResult
    private final String reportDir

    TestResult(String reportId, String testResult, String reportDir) {
        this.reportId = reportId
        this.testResult = testResult
        this.reportDir = reportDir
    }

    TestResult(TestResult result) {
        this.reportId = result.getReportId()
        this.reportDir = result.getReportDir()
        this.testResult = result.getTestResult()
    }

    @Whitelisted
    String getReportId() {
        return reportId
    }

    @Whitelisted
    String getTestResult() {
        return testResult
    }

    @Whitelisted
    String getReportDir() {
        return reportDir
    }

    @Override
    String toString() {
        """
        -> reportId: ${reportId}
        -> result: ${testResult}
        -> reportDir: ${reportDir}
        """.stripIndent().trim()
    }
}
