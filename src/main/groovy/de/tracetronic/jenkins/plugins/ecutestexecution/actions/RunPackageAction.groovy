/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.actions

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult

class RunPackageAction extends RunTestAction {

    private final PackageConfig packageConfig
    private final AnalysisConfig analysisConfig

    RunPackageAction(String testCasePath, TestConfig testConfig, PackageConfig packageConfig,
                     AnalysisConfig analysisConfig, TestResult testResult) {
        super(testCasePath, testConfig, testResult)
        this.packageConfig = packageConfig
        this.analysisConfig = analysisConfig
    }

    PackageConfig getPackageConfig() {
        return new PackageConfig(packageConfig)
    }

    AnalysisConfig getAnalysisConfig() {
        return new AnalysisConfig(analysisConfig)
    }
}
