package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.common.StandardListBoxModel
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import hudson.model.Item
import hudson.security.ACL
import hudson.util.FormValidation
import jenkins.model.Jenkins
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Run
import hudson.model.Job
import hudson.model.TaskListener
import hudson.remoting.VirtualChannel
import org.jenkinsci.plugins.workflow.steps.StepContext
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import spock.lang.Unroll

class UploadReportsStepTest extends Specification {

    @Rule
    JenkinsRule jenkins = new JenkinsRule()

    def stepContext
    def launcher
    def channel
    def envVars
    def listener
    def run
    def job
    def apiClient
    def credentials
    def printStream

    void setup() {
        stepContext = Mock(StepContext)
        launcher = Mock(Launcher)
        channel = Mock(VirtualChannel)
        envVars = Mock(EnvVars)
        listener = Mock(TaskListener)
        run = Mock(Run)
        job = Mock(Job)
        apiClient = Mock(RestApiClient)
        credentials = Mock(StandardUsernamePasswordCredentials)
        printStream = Mock(PrintStream)

        launcher.getChannel() >> channel
        stepContext.get(Launcher.class) >> launcher
        stepContext.get(EnvVars.class) >> envVars
        stepContext.get(TaskListener.class) >> listener
        stepContext.get(Run.class) >> run
        run.getParent() >> job
        listener.getLogger() >> printStream

   }

    def "Constructor should initialize with default values"() {
        when:
            def step = new UploadReportsStep("http://localhost:8085", "auth")

        then:
            step.testGuideUrl == "http://localhost:8085"
            step.credentialsId == "auth"
            step.projectId == 1
            step.useSettingsFromServer == true
            step.additionalSettings == []
            step.reportIds == []
    }

    def "Should trim trailing slash from testGuideUrl"() {
        when:
            def step = new UploadReportsStep("http://localhost:8085/", "auth")

        then:
            step.getTestGuideUrl() == "http://localhost:8085"
    }

    def "Should remove empty additional settings"() {
        given:
        def step = new UploadReportsStep("http://localhost:8085", "auth")
        def settings = [
            new AdditionalSetting("setting1", "value1"),
            new AdditionalSetting("", "empty"),
            new AdditionalSetting("setting2", "value2")
        ]

        when:
            step.setAdditionalSettings(settings)

        then:
            step.additionalSettings.size() == 2
            step.additionalSettings*.name == ["setting1", "setting2"]
            step.additionalSettings*.value == ["value1", "value2"]
    }

    def "Should remove empty report IDs"() {
        given:
            def step = new UploadReportsStep("http://localhost:8085", "credId123")
            def reportIds = ["1", "", "2", "  ", "3"]

        when:
            step.setReportIds(reportIds)

        then:
            step.reportIds == ["1", "2", "3"]
    }

    def "Descriptor should provide correct function name and display name"() {
        given:
            def descriptor = new UploadReportsStep.DescriptorImpl()

        expect:
            descriptor.getFunctionName() == 'ttUploadReports'
            descriptor.getDisplayName() == '[TT] Upload ecu.test reports to test.guide'
    }

    @Unroll
    def "doFillCredentialsIdItems should return correct items with permissions: adminPerm=#hasAdminPerm, extendedRead=#hasExtendedRead, useItem=#hasUseItem"() {
        given:
            UploadReportsStep.DescriptorImpl descriptor = new UploadReportsStep.DescriptorImpl()
            Jenkins mockJenkins = Mock(Jenkins)
            Item mockItem = itemParam

            def testCredential = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, 'testId', 'Test Credential', 'user', 'pass')
            mockJenkins.hasPermission(Jenkins.ADMINISTER) >> hasAdminPerm
            if (mockItem){
                mockItem.hasPermission(Item.EXTENDED_READ) >> hasExtendedRead
                mockItem.hasPermission(CredentialsProvider.USE_ITEM) >> hasUseItem
            }


            CredentialsProvider.metaClass.static.lookupCredentials = {
                Class type, Item item, ACL acl, List domains ->
                [testCredential]
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
            itemParam   | hasAdminPerm | hasExtendedRead | hasUseItem | currentCredentialId || expectedResult
            null        | false        | false           | false      | 'someId'            || new StandardListBoxModel().includeEmptyValue()
            null        | true         | false           | false      | null                || new StandardListBoxModel().includeEmptyValue()
            Mock(Item)  | false        | false           | false      | 'currentId'         || new StandardListBoxModel().includeCurrentValue("currentId")
            Mock(Item)  | false        | true            | false      | null                || new StandardListBoxModel().includeEmptyValue()
            Mock(Item)  | false        | false           | true       | null                || new StandardListBoxModel().includeEmptyValue()
            Mock(Item)  | false        | true            | true       | 'testId'            || new StandardListBoxModel().includeCurrentValue("testId")
    }


    @Unroll
    def "doCheckCredentialsId should validate credential '#credentialId' with permissions: extendedRead=#hasExtendedRead, useItem=#hasUseItem"() {
        given:
            UploadReportsStep.DescriptorImpl descriptor = new UploadReportsStep.DescriptorImpl()
            Jenkins mockJenkins = Mock(Jenkins)
            Item mockItem = Mock(Item)

            Jenkins.metaClass.static.get = { -> mockJenkins }
            def credentials = new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL, 'testId', 'description', 'user', 'pass')
            mockItem.hasPermission(Item.EXTENDED_READ) >> hasExtendedRead
            mockItem.hasPermission(CredentialsProvider.USE_ITEM) >> hasUseItem

            CredentialsProvider.metaClass.static.listCredentials = {
                Class type, Item item, ACL acl, List domains, Object matcher ->
                credentialId == 'testId' ? [credentials] : []
            }

        expect:
            descriptor.doCheckCredentialsId(mockItem, credentialId).kind == expectedKind

        where:
            credentialId    | hasExtendedRead | hasUseItem | expectedKind
            ''              | false           | false      | FormValidation.Kind.OK
            '${CREDS}'      | true            | false      | FormValidation.Kind.WARNING
            'nonexistent'   | true            | false      | FormValidation.Kind.ERROR
            'testId'        | true            | false      | FormValidation.Kind.ERROR
            'someId'        | false           | false      | FormValidation.Kind.OK
    }

    @Unroll
    def "doCheckCredentialsId should handle null item with adminPerm=#hasAdminPerm"() {
        given:
            UploadReportsStep.DescriptorImpl descriptor = new UploadReportsStep.DescriptorImpl()
            Jenkins mockJenkins = Mock(Jenkins)
            Jenkins.metaClass.static.get = { -> mockJenkins }
            mockJenkins.hasPermission(Jenkins.ADMINISTER) >> hasAdminPerm

        expect:
            descriptor.doCheckCredentialsId(null, credentialId).kind == expectedKind

        where:
            credentialId    | hasAdminPerm | expectedKind
            'someId'        | false         | FormValidation.Kind.OK
            'someId'        | true          | FormValidation.Kind.OK
            ''              | true          | FormValidation.Kind.OK

    }
}
