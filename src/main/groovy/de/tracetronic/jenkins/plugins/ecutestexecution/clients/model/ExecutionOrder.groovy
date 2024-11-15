/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import jline.internal.Nullable

/**
 * Abstraction of the ecu.test REST API object ExecutionOrder in all API versions.
 */
class ExecutionOrder implements Serializable {
    private static final long serialVersionUID = 1L

    public String testCasePath
    public AdditionalSettings additionalSetting
    public boolean loadConfig
    public boolean forceReload
    @Nullable public String tbcPath
    @Nullable public String tcfPath
    @Nullable public List<Constant> constants

    ExecutionOrder(String testCasePath, AdditionalSettings additionalSetting) {
        this.testCasePath = testCasePath
        this.additionalSetting = additionalSetting
    }

    ExecutionOrder(String testCasePath, TestConfig testConfig, AdditionalSettings additionalSettings) {
        this.testCasePath = testCasePath
        this.additionalSetting = additionalSettings
        this.tbcPath = testConfig.tbcPath
        this.tcfPath = testConfig.tcfPath
        this.constants = testConfig.constants
        this.loadConfig = testConfig.loadConfig
        this.forceReload = testConfig.forceConfigurationReload
    }

    /**
     * Convert the abstract ExecutionOrder object to a ecu.test REST API object of the API version V1
     * @return ExecutionOrder for ecu-test REST API in version V1
     */
    de.tracetronic.cxs.generated.et.client.model.v1.ExecutionOrder toExecutionOrderV1() {
        List<de.tracetronic.cxs.generated.et.client.model.v1.LabeledValue> constantsV1 = []
        this.constants.each {constant ->
            constantsV1.add(new de.tracetronic.cxs.generated.et.client.model.v1.LabeledValue()
                    .label(constant.label)
                    .value(constant.value))}

        return new de.tracetronic.cxs.generated.et.client.model.v1.ExecutionOrder()
                .testCasePath(this.testCasePath)
                .tbcPath(this.tbcPath)
                .tcfPath(this.tcfPath)
                .constants(constantsV1)
                .additionalSettings(this.additionalSetting.toAdditionalSettingsV1())
    }

    /**
     * Convert the abstract ExecutionOrder object to a ecu.test REST API object of the API version V2
     * @return ExecutionOrder for ecu-test REST API in version V2
     */
    de.tracetronic.cxs.generated.et.client.model.v2.ExecutionOrder toExecutionOrderV2() {
        return new de.tracetronic.cxs.generated.et.client.model.v2.ExecutionOrder()
                .testCasePath(this.testCasePath)
                .additionalSettings(this.additionalSetting.toAdditionalSettingsV2())
    }
}
