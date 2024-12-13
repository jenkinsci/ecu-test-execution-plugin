package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import spock.lang.Specification

class RunPackageStepTest extends Specification {

    def "removeEmptyParameters filters out invalid package parameters"() {
        given:
        def validParam = new PackageParameter("validLabel", "value1")
        def invalidParam = new PackageParameter("", "value2")
        def config = new PackageConfig([validParam, invalidParam])
        def step = new RunPackageStep("testPath")
        step.setPackageConfig(config)

        expect:
        step.getPackageConfig().getPackageParameters().size() == 1
        step.getPackageConfig().getPackageParameters()[0].label == "validLabel"
    }


    def "setPackageConfig assigns default when null is passed"() {
        given:
        def step = new RunPackageStep("testPath")
        step.setPackageConfig(null)

        expect:
        step.getPackageConfig().getPackageParameters() == []
    }


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
