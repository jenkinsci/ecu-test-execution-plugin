package de.tracetronic.jenkins.plugins.ecutestexecution


import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

class TGInstallationTest extends Specification {

    def "TGInstallationTest default"() {
        when:
            def config = new TGInstallation('tgInstallation', 'tg://url:0815', 'tgAuth')
        then:
            config.name == 'tgInstallation'
            config.testGuideUrl == 'tg://url:0815'
            config.projectId == 1
            config.credentialsId == 'tgAuth'
            config.useSettingsFromServer
            config.additionalSettings == []
    }

    def "TGInstallationTest with params"() {
        when:
            AdditionalSetting setting = new AdditionalSetting('key', 'value')
            def config = new TGInstallation('tgInstallation', 'tg://url:0815', 'tgAuth', 2, false, [setting])
        then:
            config.name == 'tgInstallation'
            config.testGuideUrl == 'tg://url:0815'
            config.projectId == 2
            config.credentialsId == 'tgAuth'
            !config.useSettingsFromServer
            config.additionalSettings.size() == 1
            config.additionalSettings[0].name == setting.name
            config.additionalSettings[0].value == setting.value
    }

    def "Test descriptorImpl name"() {
        given:
            def descriptor = new TGInstallation.DescriptorImpl()

        expect:
            descriptor.getDisplayName() == 'test.guide (ecu.test execution plugin)'
    }
}
