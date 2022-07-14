package de.tracetronic.jenkins.plugins.ecutestexecution.util

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import hudson.model.TaskListener

/**
 * Utility class to log test configuration to jenkins console.
 */
class LogConfigUtil implements Serializable {

    private static final long serialVersionUID = 1L

    private final TaskListener listener
    private final TestConfig testConfig
    private final PackageConfig packageConfig
    private final AnalysisConfig analysisConfig

    LogConfigUtil(TaskListener listener, TestConfig testConfig) {
        this(listener, testConfig, null, null)
    }

    LogConfigUtil(TaskListener listener, TestConfig testConfig, PackageConfig packageConfig, AnalysisConfig analysisConfig) {
        this.listener = listener
        this.testConfig = testConfig
        this.packageConfig = packageConfig
        this.analysisConfig = analysisConfig
    }

    void log() {
        logTestConfig()
        logPackageConfig()
        logAnalysisConfig()
    }

    private void logTestConfig() {
        if (testConfig.tbcPath) {
            listener.logger.println("-> With TBC=${testConfig.tbcPath}")
        }
        if (testConfig.tcfPath) {
            listener.logger.println("-> With TCF=${testConfig.tcfPath}")
        }
        if (testConfig.constants) {
            listener.logger.println("-> With global constants=[${testConfig.constants.each { it.toString() }}]")
        }
    }

    private void logPackageConfig() {
        if (packageConfig && packageConfig.packageParameters) {
            listener.logger.println("-> With package parameters=[${packageConfig.packageParameters.each { it.toString() }}]")
        }
    }

    private void logAnalysisConfig() {
        if (!analysisConfig) {
            return
        }

        if (analysisConfig.analysisName) {
            listener.logger.println("-> With analysis=${analysisConfig.analysisName}")
        }
        if (analysisConfig.mapping) {
            listener.logger.println("-> With mapping=${analysisConfig.mapping}")
        }
        if (analysisConfig.recordings) {
            listener.logger.println("-> With analysis recordings=[${analysisConfig.recordings.each { it.toString() }}]")
        }
    }
}
