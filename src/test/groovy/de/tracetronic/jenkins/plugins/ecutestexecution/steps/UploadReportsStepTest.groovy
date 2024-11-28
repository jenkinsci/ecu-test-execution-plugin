package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.cloudbees.plugins.credentials.CredentialsMatchers
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.common.StandardListBoxModel
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult
import hudson.model.Item
import hudson.security.ACL
import hudson.util.FormValidation
import jenkins.model.Jenkins
import jenkins.security.MasterToSlaveCallable
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
    static List<AdditionalSetting> additionalSettings = [
        new AdditionalSetting("setting1", "value1"),
        new AdditionalSetting("", "empty"),
        new AdditionalSetting("setting2", "value2")
    ]

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

        launcher.getChannel() >> channel
        stepContext.get(Launcher.class) >> launcher
        stepContext.get(EnvVars.class) >> envVars
        stepContext.get(TaskListener.class) >> listener
        stepContext.get(Run.class) >> run
        run.getParent() >> job
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

    def "getTestGuideUrl should trim trailing slash from testGuideUrl"() {
        when:
            def step = new UploadReportsStep("http://localhost:8085/", "auth")

        then:
            step.getTestGuideUrl() == "http://localhost:8085"
    }

    def "setAdditionalSettings should '#scenario'"() {
        given:
        def step = new UploadReportsStep("http://localhost:8085", "auth")
        when:
            step.setAdditionalSettings(given)

        then:
            step.additionalSettings.size() == resultNames.size()
            step.additionalSettings*.name == resultNames
            step.additionalSettings*.value == resultValues
        where:
            scenario                            |given                  | resultNames               | resultValues
            "remove empty additional settings"  | additionalSettings    | ["setting1","setting2"]   | ["value1","value2"]
            "handle empty list"                 | []                    | []                        | []
            "handle null"                       | null                  | []                        | []

    }

    def "setReportIds should '#scenario'"() {
        given:
            def step = new UploadReportsStep("http://localhost:8085", "credId123")

        when:
            step.setReportIds(given)

        then:
            step.reportIds == result
        where:
            scenario            |given                          | result
            "remove empty ids"  | ["1", "", "2", "  ", "3"]     | ["1", "2", "3"]
            "handle empty list" | []                            | []
            "handle null"       | null                          | []
    }

    @Unroll
    def "Should handle '#scenario' report upload"() {
        given:
            def logger = Mock(PrintStream)
            def step = new UploadReportsStep("http://localhost:8085", "credId123")
            step.setReportIds(["1"])
            def execution = new UploadReportsStep.Execution(step, stepContext)
            GroovyMock(RestApiClientFactory, global: true)

            def mockCredential = Mock(StandardUsernamePasswordCredentials) {
                getId() >> "credId123"
            }

            GroovyMock(CredentialsMatchers, global: true)
            CredentialsMatchers.firstOrNull(_, CredentialsMatchers.withId("credId123")) >> mockCredential

        and:
            listener.logger >> logger

            envVars.expand("1") >> "1"
            envVars.expand("http://localhost:8085") >> "http://localhost:8085"
            listener.logger >> logger
            RestApiClientFactory.getRestApiClient(*_) >> apiClient

            apiClient.uploadReport(_, _) >> new UploadResult("Success", "message", link)
            channel.call(_) >> { MasterToSlaveCallable callable ->
                return callable.call()
            }

        when:
            def results = execution.run()

        then:
            results.size() == 1
            results.every {
                        it.uploadResult == "Success" &&
                        it.uploadMessage == "message" &&
                        it.reportLink == link
            }
            1 * logger.println("Uploading reports to test.guide http://localhost:8085...")
            1 * logger.println("- Uploading ATX report for report id 1...")
            1 * logger.println("  -> message")
            1 * logger.println(resultPrint)


        where:
            scenario    | link    | resultPrint
            "stable"    | "link"  | "Report upload(s) successful"
            "unstable"  | ""      | "Report upload(s) unstable. Please see the logging of the uploads."
    }

        def "Should call getAllReportIds if '#scenario'"() {
            given:
                def step = new UploadReportsStep("http://localhost:8085", "credId123")
                if(given != "skip"){
                    step.setReportIds(given)
                }
                def execution = new UploadReportsStep.Execution(step, stepContext)
                GroovyMock(RestApiClientFactory, global: true)

            and:
                RestApiClientFactory.getRestApiClient(*_) >> apiClient
                apiClient.uploadReport(_, _) >> new UploadResult("Success", "message", "folder")
                channel.call(_) >> { MasterToSlaveCallable callable ->
                    return callable.call()
                }

            when:
                execution.run()
            then:
                1 * apiClient.getAllReportIds()
            where:
                scenario             | given
                "report ids not set" | "skip"
                "report ids null"    | null
                "report empty"       | []
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
