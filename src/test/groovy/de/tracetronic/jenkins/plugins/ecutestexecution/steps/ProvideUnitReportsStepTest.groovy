package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import hudson.EnvVars
import hudson.Launcher
import hudson.model.TaskListener
import spock.lang.Specification

class ProvideUnitReportsStepTest extends Specification {

    def "Test setUnstableThreshold"() {
        given:
            def step = new ProvideUnitReportsStep()
        when:
            step.setUnstableThreshold(value)
        then:
            expected == step.unstableThreshold
        where:
            value   | expected
            -5.0    | 0.0
            -0.0001 | 0.0
            0.0     | 0.0
            0.01    | 0.01
            42.21   | 42.21
            99.999  | 99.999
            100.0   | 100.0
            100.0001| 100.0
            150.0   | 100.0
    }

    def "Test setFailedThreshold"() {
        given:
            def step = new ProvideUnitReportsStep()
        when:
            step.setFailedThreshold(value)
        then:
            expected == step.failedThreshold
        where:
            value   | expected
            -5.0    | 0.0
            -0.0001 | 0.0
            0.0     | 0.0
            0.01    | 0.01
            42.21   | 42.21
            99.999  | 99.999
            100.0   | 100.0
            100.0001| 100.0
            150.0   | 100.0
    }

    def "Test setReportGlob"() {
        given:
            def step = new ProvideUnitReportsStep()
        when:
            step.setReportGlob(value)
        then:
            expected == step.reportGlob
        where:
            value       | expected
            ""          | ProvideUnitReportsStep.DEFAULT_REPORT_GLOB
            null        | ProvideUnitReportsStep.DEFAULT_REPORT_GLOB
            "  "        | ProvideUnitReportsStep.DEFAULT_REPORT_GLOB
            "**/*.xml"  | "**/*.xml"
            " **/*.xml "| "**/*.xml"
    }

    def "Test DescriptorImpl returns correct values"() {
        given:
            def descriptor = new ProvideUnitReportsStep.DescriptorImpl()
        expect:
            descriptor.functionName == 'ttProvideUnitReports'
            descriptor.displayName == '[TT] Provide generated unit reports as job test results.'
            descriptor.requiredContext == [Launcher, EnvVars, TaskListener] as Set
    }
}
