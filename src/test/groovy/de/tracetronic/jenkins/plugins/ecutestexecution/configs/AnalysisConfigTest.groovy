package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.steps.RunPackageStep
import spock.lang.Specification

class AnalysisConfigTest extends Specification {
    def "setAnalysisConfig assigns provided value"() {
        given:
            def config = new AnalysisConfig()
            config.setAnalysisName("My Analysis")

            def step = new RunPackageStep("testPath")
            step.setAnalysisConfig(config)

        expect:
            step.getAnalysisConfig().getAnalysisName() == "My Analysis"
    }

    def "setAnalysisConfig assigns default when null is passed"() {
        given:
            def step = new RunPackageStep("testPath")
            step.setAnalysisConfig(null)

        expect:
            step.getAnalysisConfig().getAnalysisName() == ""
            step.getAnalysisConfig().getMapping() == ""
            step.getAnalysisConfig().getRecordings().isEmpty()
    }
}
