package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.cxs.generated.et.client.model.AdditionalSettings
import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.LabeledValue
import de.tracetronic.cxs.generated.et.client.model.Recording
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig

/**
 * This class provides a means to build an ExecutionOrder on demand, depending on the given configurations. Since the
 * ExecutionOrder is itself nonserializable, this enables to build it after a serialization-deserialization process and
 * thus avoid serialization errors.
 */
class ExecutionOrderBuilder implements Serializable {

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
        this(testCasePath, testConfig)
        this.packageConfig = packageConfig
        this.analysisConfig = analysisConfig
        isPackage = true
    }

    /**
     * Field constructor for the configuration defined in {@link: TestProjectBuilder}
     * @param testCasePath
     * @param testConfig
     */
    ExecutionOrderBuilder(String testCasePath, TestConfig testConfig) {
        this.testCasePath = testCasePath
        this.testConfig = testConfig
        isPackage = false
    }

    /**
     * Build the execution order.
     * @return ExecutionOrder
     */
    ExecutionOrder build() {
        AdditionalSettings settings
        if (isPackage) {
            settings = new AdditionalSettings()
                .forceConfigurationReload(testConfig.forceConfigurationReload)
                .packageParameters(packageConfig.packageParameters as List<LabeledValue>)
                .analysisName(analysisConfig.analysisName)
                .mapping(analysisConfig.mapping)
                .recordings(analysisConfig.recordings as List<Recording>)
        }
        else {
            settings = new AdditionalSettings()
                .forceConfigurationReload(testConfig.forceConfigurationReload)
        }

        ExecutionOrder executionOrder = new ExecutionOrder()
                .testCasePath(testCasePath)
                .tbcPath(testConfig.tbcPath)
                .tcfPath(testConfig.tcfPath)
                .constants(testConfig.constants as List<LabeledValue>)
                .additionalSettings(settings)

        return executionOrder
    }
}
