package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import hudson.EnvVars
import hudson.util.FormValidation
import net.sf.json.JSONObject
import spock.lang.Specification

class TestConfigTest extends Specification {

    def "TestConfig default"() {
        when:
            def config = new TestConfig()
        then:
            !config.loadConfig
            config.tbcPath == null
            config.tcfPath == null
            !config.forceConfigurationReload
            config.constants == []
    }

    def "TestConfig with config"() {
        given:
            def originalConfig = new TestConfig()
            originalConfig.tbcPath = "test.tbc"
            originalConfig.tcfPath = "test.tcf"
            originalConfig.forceConfigurationReload = true
            originalConfig.constants = [new Constant("test", "value")]
        when:
            def createdTestConfig = new TestConfig(originalConfig)
        then:
            createdTestConfig.loadConfig
            createdTestConfig.tbcPath == "test.tbc"
            createdTestConfig.tcfPath == "test.tcf"
            createdTestConfig.forceConfigurationReload
            createdTestConfig.constants.size() == 1
            createdTestConfig.constants[0].label == "test"
            createdTestConfig.constants[0].value == "value"
    }

    def "TestConfig loadConfig branches"() {
        given:
            def originalConfig = new TestConfig()
            originalConfig.tcfPath = tcfPath
            originalConfig.tbcPath = tbcPath
        when:
            def createdTestConfig = new TestConfig(originalConfig)
        then:
            createdTestConfig.loadConfig == expectedLoadConfig
        where:
            tcfPath     | tbcPath     | expectedLoadConfig
            ""          | ""          | true
            "test.tcf"  | null        | true
            null        | "test.tbc"  | true
            "test.tcf"  | "test.tbc"  | true
            null        | null        | false

    }

    def "TestConfig set loadConfig true"() {
        given:
            def originalConfig = new TestConfig()
            originalConfig.tbcPath = ""
            originalConfig.tcfPath = ""
        when:
            def createdTestConfig = new TestConfig(originalConfig)
        then:
            createdTestConfig.loadConfig
            createdTestConfig.tbcPath == ""
            createdTestConfig.tcfPath == ""
    }

    def "Expand environment variables"() {
        given:
            def config = new TestConfig()
            config.tbcPath = '${TBC_PATH}.tbc'
            config.tcfPath = '${TCF_PATH}.tcf'
            config.constants = [new Constant("test", '${TEST_VALUE}')]
        and:
            def envVars = new EnvVars()
            envVars.put("TBC_PATH", "test")
            envVars.put("TCF_PATH", "test")
            envVars.put("TEST_VALUE", "value")
        when:
            def expandedTestConfig = config.expand(envVars)
        then:
            expandedTestConfig.tbcPath == 'test.tbc'
            expandedTestConfig.tcfPath == 'test.tcf'
            expandedTestConfig.constants[0].value == 'value'
    }

    def "Remove empty constants"() {
        given:
            def config = new TestConfig()
            config.constants = [
                    new Constant("test", "value"),
                    new Constant("", "value1"),
                    new Constant("  ", "value3")
            ]
        expect:
            config.constants.size() == 1
            config.constants[0].label == "test"
            config.constants[0].value == "value"
    }

    def "processFormData with loadConfig=#loadConfig"() {
        given:
            def formData = new JSONObject()
            if (loadConfig != null) {
                formData.put("loadConfig", loadConfig)
            }
            formData.put("tbcPath", "test.tbc")
            formData.put("tcfPath", "test.tcf")
        when:
            def result = TestConfig.DescriptorImpl.processFormData(formData)
        then:
            result.containsKey("tbcPath") == shouldContainPaths
            result.containsKey("tcfPath") == shouldContainPaths
        where:
            loadConfig  || shouldContainPaths
            true        || true
            false       || false
            null        || true
    }

    def "processFormData empty data"() {
        given:
            def formData = new JSONObject()
        when:
            def result = TestConfig.DescriptorImpl.processFormData(formData)
        then:
            !result.containsKey("tbcPath")
            !result.containsKey("tcfPath")
    }

    def "processFormData not modify other properties"() {
        given:
            def formData = new JSONObject()
            formData.put("loadConfig", false)
            formData.put("tbcPath", "test.tbc")
            formData.put("tcfPath", "test.tcf")
            formData.put("forceConfigurationReload", false)
            formData.put("constants", [])
        when:
            def result = TestConfig.DescriptorImpl.processFormData(formData)
        then:
            !result.containsKey("tbcPath")
            !result.containsKey("tcfPath")
            !result["forceConfigurationReload"]
            result["constants"].isEmpty()
    }

    def "processFormData passing paths data"() {
        given:
            def formData = new JSONObject()
            formData.put("loadConfig", true)
            formData.put("tbcPath", tbcPath)
            formData.put("tcfPath", tcfPath)
        when:
            def result = TestConfig.DescriptorImpl.processFormData(formData)
        then:
            result.getString("tbcPath") == tbcPath
            result.getString("tcfPath") == tcfPath
        where:
            tbcPath     | tcfPath
            "test.tbc"  | "test.tcf"
            ""          | "test.tcf"
            "test.tbc"  | ""
            ""          | ""
    }

    def "Validate file tbc extensions"() {
        given:
            def descriptor = new TestConfig.DescriptorImpl()
        expect:
            descriptor.doCheckTbcPath(path).kind == expectedKind
        where:
            path              | expectedKind
            'test.tbc'        | FormValidation.Kind.OK
            'test.prj'        | FormValidation.Kind.ERROR
            ''                | FormValidation.Kind.OK
            null              | FormValidation.Kind.OK
    }

    def "Validate file tcf extensions"() {
        given:
            def descriptor = new TestConfig.DescriptorImpl()
        expect:
            descriptor.doCheckTcfPath(path).kind == expectedKind
        where:
            path              | expectedKind
            'test.tcf'        | FormValidation.Kind.OK
            'test.prj'        | FormValidation.Kind.ERROR
            ''                | FormValidation.Kind.OK
            null              | FormValidation.Kind.OK
    }

    def "Test descriptorImpl name"() {
        given:
            def descriptor = new TestConfig.DescriptorImpl()
        expect:
            descriptor.getDisplayName() == 'TestConfig'
    }
}
