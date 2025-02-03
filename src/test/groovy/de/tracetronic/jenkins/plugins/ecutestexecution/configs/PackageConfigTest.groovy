package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.steps.RunPackageStep
import spock.lang.Specification

class PackageConfigTest extends Specification {
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
}
