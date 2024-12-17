package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig

class RunTestStepTest extends Specification {

    def "setTestConfig assigns the provided configuration"() {
        given:
            def step = Spy(RunTestStep, constructorArgs: ["testPath"])
            def customConfig = new TestConfig()
            customConfig.setTbcPath("custom/path/to.tbc")
            customConfig.setTcfPath("custom/path/to.tcf")
            customConfig.setForceConfigurationReload(true)

        when:
            step.setTestConfig(customConfig)

        then:
            def testConfig = step.getTestConfig()
            testConfig.tbcPath == "custom/path/to.tbc"
            testConfig.tcfPath == "custom/path/to.tcf"
            testConfig.forceConfigurationReload
    }

    def "setTestConfig assigns default configuration when null is passed"() {
        given:
            def step = Spy(RunTestStep, constructorArgs: ["testPath"])

        when:
            step.setTestConfig(null)

        then:
            def testConfig = step.getTestConfig()
            testConfig.tbcPath == null
            testConfig.tcfPath == null
            testConfig.constants.isEmpty()
            !testConfig.forceConfigurationReload
    }

    def "setExecutionConfig assigns the provided configuration"() {
        given:
            def step = Spy(RunTestStep, constructorArgs: ["testPath"])
            def customConfig = new ExecutionConfig()
            customConfig.setTimeout(5000)
            customConfig.setStopOnError(false)

        when:
            step.setExecutionConfig(customConfig)

        then:
            def executionConfig = step.getExecutionConfig()
            executionConfig.timeout == 5000
            !executionConfig.stopOnError
    }

    def "setExecutionConfig assigns default configuration when null is passed"() {
        given:
            def step = Spy(RunTestStep, constructorArgs: ["testPath"])

        when:
            step.setExecutionConfig(null)

        then:
            def executionConfig = step.getExecutionConfig()
            executionConfig.timeout == ExecutionConfig.DEFAULT_TIMEOUT
            executionConfig.stopOnError
            executionConfig.getStopUndefinedTools()
            !executionConfig.getExecutePackageCheck()
    }
}

