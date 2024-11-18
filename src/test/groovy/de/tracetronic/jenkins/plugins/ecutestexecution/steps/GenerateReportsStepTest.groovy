package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import hudson.remoting.VirtualChannel
import spock.lang.Specification
import spock.lang.Unroll
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import jenkins.security.MasterToSlaveCallable
import org.jenkinsci.plugins.workflow.steps.StepContext
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult

class GenerateReportsStepTest extends Specification {

    def stepContext
    def launcher
    def channel
    def envVars
    def listener
    def run
    def apiClient

    void setup() {
        stepContext = Mock(StepContext)
        launcher = Mock(Launcher)
        channel = Mock(VirtualChannel)
        envVars = Mock(EnvVars)
        listener = Mock(TaskListener)
        run = Mock(Run)
        apiClient = Mock(RestApiClient)

        launcher.getChannel() >> channel
        stepContext.get(Launcher.class) >> launcher
        stepContext.get(EnvVars.class) >> envVars
        stepContext.get(TaskListener.class) >> listener
        stepContext.get(Run.class) >> run
        listener.getLogger() >> new PrintStream(new ByteArrayOutputStream())
    }

    def "Constructor should initialize with default values"() {
        when:
            def step = new GenerateReportsStep("HTML")

        then:
            step.generatorName == "HTML"
            step.additionalSettings == []
            step.reportIds == []
    }

    def "Constructor should trim generator name"() {
        when:
            def step = new GenerateReportsStep("  HTML  ")

        then:
            step.generatorName == "HTML"
    }

    def "Should remove empty additional settings"() {
        given:
            def step = new GenerateReportsStep("HTML")
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
            def step = new GenerateReportsStep("HTML")
            def reportIds = ["1", "", "2", "  ", "3"]

        when:
            step.setReportIds(reportIds)

        then:
            step.reportIds == ["1", "2", "3"]
    }

    @Unroll
    def "Should handle report generation for generator '#generator'"() {
        given:
            def step = new GenerateReportsStep(generator)
            step.setReportIds(["1", "2"])
            def execution = new GenerateReportsStep.Execution(step, stepContext)
            def printStream = Mock(PrintStream)
            GroovyMock(RestApiClientFactory, global: true)

        and:
            listener.getLogger() >> printStream
            RestApiClientFactory.getRestApiClient(*_) >> apiClient

            apiClient.generateReport(_, _) >> new GenerationResult("Success", "message", "folder")
            channel.call(_) >> { MasterToSlaveCallable callable ->
                return callable.call()
            }

        when:
            def results = execution.run()

        then:
            results.size() == 2
            results.every {
                        it.generationResult == "Success" &&
                        it.generationMessage == "message" &&
                        it.reportOutputDir == "folder"
            }

        where:
            generator << ['HTML', 'ATX', 'EXCEL', 'JSON']
    }

    def "Descriptor should provide correct function name and display name"() {
        given:
            def descriptor = new GenerateReportsStep.DescriptorImpl()

        expect:
            descriptor.getFunctionName() == 'ttGenerateReports'
            descriptor.getDisplayName() == '[TT] Generate ecu.test reports'
    }

    def "Descriptor should provide valid generator names"() {
        given:
            def descriptor = new GenerateReportsStep.DescriptorImpl()

        when:
            def items = descriptor.doFillGeneratorNameItems()

        then:
            items.size() == 7
            items*.name as Set == ['ATX', 'EXCEL', 'HTML', 'JSON', 'TRF-SPLIT', 'TXT', 'UNIT'] as Set
    }
}
