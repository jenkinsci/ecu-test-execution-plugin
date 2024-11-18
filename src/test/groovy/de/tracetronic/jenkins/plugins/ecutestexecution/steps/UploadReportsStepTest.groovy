package de.tracetronic.jenkins.plugins.ecutestexecution.steps
import spock.lang.Specification
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Run
import hudson.model.Job
import hudson.model.TaskListener
import hudson.remoting.VirtualChannel
import org.jenkinsci.plugins.workflow.steps.StepContext
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import hudson.util.Secret
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting

class UploadReportsStepTest extends Specification {

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

        // Mock credentials
        credentials.getPassword() >> Secret.fromString("testAuthKey")
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
}
