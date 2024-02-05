/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.AdditionalSettings
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.LabeledValue
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ConverterUtil

/**
 * This class provides a means to build an ExecutionOrder on demand, depending on the given configurations. Since the
 * ExecutionOrder is itself nonserializable, this enables to build it after a serialization-deserialization process and
 * thus avoid serialization errors.
 */
class ExecutionOrderBuilder implements Serializable {
    
    private static final long serialVersionUID = 1L

    private final String testCasePath
    private final TestConfig testConfig
    private final PackageConfig packageConfig
    private final AnalysisConfig analysisConfig
    private boolean isPackage

    /**
     * field constructor for the configuration defined in {@link TestPackageBuilder}
     * @param testCasePath
     * @param testConfig
     * @param packageConfig
     * @param analysisConfig
     */
    ExecutionOrderBuilder(String testCasePath, TestConfig testConfig, PackageConfig packageConfig, AnalysisConfig analysisConfig) {
        this.testCasePath = testCasePath
        this.testConfig = testConfig
        this.packageConfig = packageConfig
        this.analysisConfig = analysisConfig
        this.isPackage = true
    }

    /**
     * Field constructor for the configuration defined in {@link: TestProjectBuilder}
     * @param testCasePath
     * @param testConfig
     */
    ExecutionOrderBuilder(String testCasePath, TestConfig testConfig) {
        this.testCasePath = testCasePath
        this.testConfig = testConfig
        this.packageConfig = null
        this.analysisConfig = null
        this.isPackage = false
    }

    /**
     * Build the execution order.
     * @return ExecutionOrder
     */
    ExecutionOrder build() {
        AdditionalSettings settings
        if (isPackage) {

            settings = new AdditionalSettings(analysisConfig.analysisName,
                    ConverterUtil.recordingConverter(analysisConfig.recordings), analysisConfig.mapping,
                    testConfig.forceConfigurationReload, ConverterUtil.labeledValueConverter(packageConfig.packageParameters))
        }
        else {
            settings = new AdditionalSettings(testConfig.forceConfigurationReload)
        }

        ExecutionOrder executionOrder = new ExecutionOrder(testCasePath, settings, testConfig.tbcPath,
                testConfig.tcfPath, testConfig.constants)

        return executionOrder
    }
}
