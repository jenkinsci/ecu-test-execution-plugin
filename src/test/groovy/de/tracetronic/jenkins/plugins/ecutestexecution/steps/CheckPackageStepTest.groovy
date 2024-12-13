package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import spock.lang.Specification

class CheckPackageStepTest extends Specification {

    def "setExecutionConfig sets default ExecutionConfig when input is null"() {
        given:
        def step = new CheckPackageStep("test/path")

        when:
        step.setExecutionConfig(null)

        then:
        step.getExecutionConfig() != null
        step.getExecutionConfig() instanceof ExecutionConfig
        step.getExecutionConfig().timeout == ExecutionConfig.DEFAULT_TIMEOUT
    }

    def "setExecutionConfig creates a copy of the provided ExecutionConfig when input is non-null"() {
        given:
        def step = new CheckPackageStep("test/path")
        def customConfig = new ExecutionConfig()
        customConfig.setTimeout(5000)

        when:
        step.setExecutionConfig(customConfig)

        then:
        step.getExecutionConfig() != customConfig
        step.getExecutionConfig().timeout == customConfig.timeout
    }
}
