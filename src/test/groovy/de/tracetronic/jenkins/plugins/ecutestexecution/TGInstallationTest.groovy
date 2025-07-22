package de.tracetronic.jenkins.plugins.ecutestexecution

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardListBoxModel
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import hudson.model.Item
import hudson.util.FormValidation
import hudson.util.Messages
import jenkins.model.Jenkins
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

class TGInstallationTest extends Specification {

    @Rule
    JenkinsRule jenkins = new JenkinsRule()

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

    def "Test descriptorImpl checkName"(){
        given:
            def descriptor = new TGInstallation.DescriptorImpl()
        expect:
            assert (descriptor.doCheckName(name).message == result);
        where:
            name | result
            "tg" | FormValidation.ok().message
            ""   | Messages.FormValidation_ValidateRequired()
            null | Messages.FormValidation_ValidateRequired()
    }

    def "Test descriptorImpl doCheckTestGuideUrl"(){
        given:
            def descriptor = new TGInstallation.DescriptorImpl()
        expect:
            assert (descriptor.doCheckTestGuideUrl(url).message == result);
        where:
            url                     | result
            "http://localhost:0815" | FormValidation.ok().message
            ""                      | Messages.FormValidation_ValidateRequired()
            null                    | Messages.FormValidation_ValidateRequired()
    }

    def "Test descriptorImpl doCheckProjectId"(){
        given:
            def descriptor = new TGInstallation.DescriptorImpl()
        expect:
            assert (descriptor.doCheckProjectId(projectId).message == result);
        where:
            projectId       | result
            "1"             | FormValidation.ok().message
            "0"             | FormValidation.error("Value must be an integer greater than 0.").message
            "-1"            | FormValidation.error("Value must be an integer greater than 0.").message
            "A"             | FormValidation.error("Value must be an integer greater than 0.").message
            ""              | Messages.FormValidation_ValidateRequired()
            null            | Messages.FormValidation_ValidateRequired()
    }

    def "doFillCredentialsIdItems should return correct items with permissions: adminPerm=#hasAdminPerm, extendedRead=#hasExtendedRead, useItem=#hasUseItem"() {
        given:
            TGInstallation.DescriptorImpl descriptor = new TGInstallation.DescriptorImpl()
            Jenkins mockJenkins = Mock(Jenkins)
            Item mockItem = itemParam ? Mock(Item) : null
            mockJenkins.hasPermission(Jenkins.ADMINISTER) >> hasAdminPerm
            if (mockItem){
                mockItem.hasPermission(Item.EXTENDED_READ) >> hasExtendedRead
                mockItem.hasPermission(CredentialsProvider.USE_ITEM) >> hasUseItem
            }
        when:
            def result = descriptor.doFillCredentialsIdItems(mockItem, currentCredentialId)
        then:
            assert result.size() == expectedResult.size()
            result.eachWithIndex { item, idx ->
                assert item.name == expectedResult[idx].name
                assert item.value == expectedResult[idx].value
            }
        where:
            itemParam   | hasAdminPerm | hasExtendedRead | hasUseItem | currentCredentialId | expectedResult
            null        | false        | false           | false      | 'someId'            | new StandardListBoxModel().includeEmptyValue()
            null        | true         | false           | false      | null                | new StandardListBoxModel().includeEmptyValue()
            Mock(Item)  | false        | false           | false      | 'currentId'         | new StandardListBoxModel().includeCurrentValue("currentId")
            Mock(Item)  | false        | true            | false      | null                | new StandardListBoxModel().includeEmptyValue()
            Mock(Item)  | false        | false           | true       | null                | new StandardListBoxModel().includeEmptyValue()
    }

    def "doCheckCredentialsId should validate credential '#credentialId' with permissions: extendedRead=#hasExtendedRead, useItem=#hasUseItem"() {
        given:
            TGInstallation.DescriptorImpl descriptor = new TGInstallation.DescriptorImpl()
            Jenkins mockJenkins = Mock(Jenkins)

            Item mockItem = itemParam ? Mock(Item) : null
            Jenkins.metaClass.static.get = { -> mockJenkins }
            mockJenkins.hasPermission(Jenkins.ADMINISTER) >> hasAdminPerm
            if (mockItem) {
                mockItem.hasPermission(Item.EXTENDED_READ) >> hasExtendedRead
                mockItem.hasPermission(CredentialsProvider.USE_ITEM) >> hasUseItem
            }
        when:
            def kind = descriptor.doCheckCredentialsId(mockItem, credentialId).kind
        then:
            kind == expectedKind
        cleanup:
            Jenkins.metaClass = null
        where:
            itemParam   | hasAdminPerm  | credentialId  | hasExtendedRead   | hasUseItem | expectedKind
            null        | false         | ''            | false             | false      | FormValidation.Kind.OK
            Mock(Item)  | true          | ''            | false             | false      | FormValidation.Kind.OK
            Mock(Item)  | true          | ''            | true              | true       | FormValidation.Kind.OK
            Mock(Item)  | true          | '${CREDS}'    | true              | true       | FormValidation.Kind.WARNING
            Mock(Item)  | true          | 'nonexistent' | true              | true       | FormValidation.Kind.ERROR
    }

    def "doCheckCredentialsId should handle null item with adminPerm=#hasAdminPerm"() {
        given:
            TGInstallation.DescriptorImpl descriptor = new TGInstallation.DescriptorImpl()
            Jenkins mockJenkins = Mock(Jenkins)
            Jenkins.metaClass.static.get = { -> mockJenkins }
            mockJenkins.hasPermission(Jenkins.ADMINISTER) >> hasAdminPerm
        when:
            def kind = descriptor.doCheckCredentialsId(null, credentialId).kind
        then:
            kind == expectedKind
        cleanup:
            Jenkins.metaClass = null
        where:
            credentialId    | hasAdminPerm | expectedKind
            'someId'        | false         | FormValidation.Kind.OK
            'someId'        | true          | FormValidation.Kind.ERROR
            ''              | true          | FormValidation.Kind.OK
    }
}
