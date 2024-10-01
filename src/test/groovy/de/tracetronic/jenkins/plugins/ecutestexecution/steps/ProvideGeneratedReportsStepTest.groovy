package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification
import hudson.EnvVars
import hudson.Launcher
import hudson.model.TaskListener

class ProvideGeneratedReportsStepTest extends Specification {

    def "Test DescriptorImpl returns correct values"() {
        given:
            def descriptor = new ProvideGeneratedReportsStep.DescriptorImpl()
        expect:
            descriptor.functionName == 'ttProvideGeneratedReports'
            descriptor.displayName == '[TT] Provide generated ecu.test reports as job artifacts.'
            descriptor.requiredContext == [Launcher, EnvVars, TaskListener] as Set
    }
}
