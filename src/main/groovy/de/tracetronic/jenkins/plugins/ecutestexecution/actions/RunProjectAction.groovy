/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.actions

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult

class RunProjectAction extends RunTestAction {

    RunProjectAction(String testCasePath, TestConfig testConfig, TestResult testResult) {
        super(testCasePath, testConfig, testResult)
    }
}
