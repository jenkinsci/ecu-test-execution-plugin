package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.cloudbees.plugins.credentials.CredentialsMatchers
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardListBoxModel
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult
import hudson.model.Item
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
        launcher.getChannel() >> channel
        stepContext.get(Launcher.class) >> launcher
        stepContext.get(EnvVars.class) >> envVars
        stepContext.get(TaskListener.class) >> listener
        stepContext.get(Run.class) >> run
        run.getParent() >> job
   }

    def "Default constructor"() {
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

    def "setAdditionalSettings should handle '#given'"() {
        given:
            def step = new UploadReportsStep("http://localhost:8085", "auth")
        when:
            step.setAdditionalSettings(given)
        then:
            step.additionalSettings.size() == resultNames.size()
            step.additionalSettings*.name == resultNames
            step.additionalSettings*.value == resultValues
        where:
            given                 | resultNames               | resultValues
            additionalSettings    | ["setting1","setting2"]   | ["value1","value2"]
            []                    | []                        | []
            null                  | []                        | []

    }

    def "setReportIds should handle '#given'"() {
        given:
            def step = new UploadReportsStep("http://localhost:8085", "credId123")
        when:
            step.setReportIds(given)
        then:
            step.reportIds == result
        where:
            given                         | result
            ["1", "", "2", "  ", "3"]     | ["1", "2", "3"]
            []                            | []
            null                          | []
    }

    def "Should handle report upload for link:'#link'"() {
        given:
            def logger = Mock(PrintStream)
            def step = new UploadReportsStep("http://localhost:8085", "credId123")
            step.setReportIds(givenReportIds)
            def execution = new UploadReportsStep.Execution(step, stepContext)
            GroovyMock(RestApiClientFactory, global: true)

            def mockCredential = Mock(StandardUsernamePasswordCredentials) {
                getId() >> "credId123"
            }
            GroovyMock(CredentialsMatchers, global: true)
            CredentialsMatchers.firstOrNull(_, CredentialsMatchers.withId("credId123")) >> mockCredential
        and:
            listener.logger >> logger
            for (def reportId in givenReportIds){
                envVars.expand(reportId) >> reportId
            }
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
            results.size() == givenReportIds.size()
            results.every {
                        it.uploadResult == "Success" &&
                        it.uploadMessage == "message" &&
                        it.reportLink == link
            }
            1 * logger.println("Uploading reports to test.guide http://localhost:8085...")
            for (def reportId in givenReportIds){
                1 * logger.println("- Uploading ATX report for report id ${reportId}...")
            }
            givenReportIds.size() * logger.println("  -> message")
            1 * logger.println(resultPrint)
        where:
            link    | givenReportIds    | resultPrint
            "link"  | ["1"]             |"Report upload(s) successful"
            "link"  | ["1", "2"]        |"Report upload(s) successful"
            ""      | ["1"]             |"Report upload(s) unstable. Please see the logging of the uploads."
            ""      | ["1", "2"]        |"Report upload(s) unstable. Please see the logging of the uploads."
    }

    def "Call getAllReportIds if setReportIds with: '#given'"() {
        given:
            def step = new UploadReportsStep("http://localhost:8085", "credId123")
            if(given != "skip"){
                step.setReportIds(given)
            }
            def execution = new UploadReportsStep.Execution(step, stepContext)
            GroovyMock(RestApiClientFactory, global: true)
            def mockCredential = Mock(StandardUsernamePasswordCredentials) {
                getId() >> "credId123"
            }

            GroovyMock(CredentialsMatchers, global: true)
            CredentialsMatchers.firstOrNull(_, CredentialsMatchers.withId("credId123")) >> mockCredential
        and:
            RestApiClientFactory.getRestApiClient(*_) >> apiClient
            apiClient.uploadReport(_, _) >> new UploadResult("Success", "message", "folder")
            channel.call(_) >> { MasterToSlaveCallable callable ->
                return callable.call()
            }
        when:
            execution.run()
        then:
            calledCount * apiClient.getAllReportIds()
        where:
            given    | calledCount
            "skip"   | 1
            null     | 1
            []       | 1
            ["1"]    | 0
    }



    def "Descriptor should provide correct function name and display name"() {
        given:
            def descriptor = new UploadReportsStep.DescriptorImpl()
        expect:
            descriptor.getFunctionName() == 'ttUploadReports'
            descriptor.getDisplayName() == '[TT] Upload ecu.test reports to test.guide'
    }

    def "doFillCredentialsIdItems should return correct items with permissions: adminPerm=#hasAdminPerm, extendedRead=#hasExtendedRead, useItem=#hasUseItem"() {
        given:
            UploadReportsStep.DescriptorImpl descriptor = new UploadReportsStep.DescriptorImpl()
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
            itemParam   | hasAdminPerm | hasExtendedRead | hasUseItem | currentCredentialId || expectedResult
            null        | false        | false           | false      | 'someId'            || new StandardListBoxModel().includeEmptyValue()
            null        | true         | false           | false      | null                || new StandardListBoxModel().includeEmptyValue()
            Mock(Item)  | false        | false           | false      | 'currentId'         || new StandardListBoxModel().includeCurrentValue("currentId")
            Mock(Item)  | false        | true            | false      | null                || new StandardListBoxModel().includeEmptyValue()
            Mock(Item)  | false        | false           | true       | null                || new StandardListBoxModel().includeEmptyValue()
    }

    def "doCheckCredentialsId should validate credential '#credentialId' with permissions: extendedRead=#hasExtendedRead, useItem=#hasUseItem"() {
        given:
            UploadReportsStep.DescriptorImpl descriptor = new UploadReportsStep.DescriptorImpl()
            Jenkins mockJenkins = Mock(Jenkins)

            Item mockItem = itemParam ? Mock(Item) : null
            Jenkins.metaClass.static.get = { -> mockJenkins }
            mockJenkins.hasPermission(Jenkins.ADMINISTER) >> hasAdminPerm
            if (mockItem) {
                mockItem.hasPermission(Item.EXTENDED_READ) >> hasExtendedRead
                mockItem.hasPermission(CredentialsProvider.USE_ITEM) >> hasUseItem
            }


        expect:
            descriptor.doCheckCredentialsId(mockItem, credentialId).kind == expectedKind

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

    def "should correctly handle different credential strings based on the format"() {
        given:
            def value = inputValue

        when:
            def result = UploadReportsStep.DescriptorImpl.isExpressionBasedCredentials(value)

        then:
            result == expectedResult

        where:
            inputValue            | expectedResult
            "\${expression}"      | true
            "invalidExpression"   | false
            "\${expression"       | false
            "expression}"         | false
    }
}
