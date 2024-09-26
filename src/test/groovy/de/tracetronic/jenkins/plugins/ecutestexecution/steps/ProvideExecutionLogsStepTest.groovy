package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import spock.lang.Specification
import hudson.EnvVars
import hudson.Launcher
import hudson.model.TaskListener

class ProvideExecutionLogsStepTest extends Specification {
    def "Test processReport handles #scenario"() {
        given:
            def step = new ProvideExecutionLogsStep()
            def reportZip = Mock(File)
            def reportDirName = "TestReport"
            def outDirPath = "/tmp/output"
            def listener = Mock(TaskListener)
            def logger = Mock(PrintStream)

        and:
            GroovyMock(ZipUtil, global: true)
            ZipUtil.extractFilesByExtension(_, _, _) >> extractedFiles
            listener.logger >> logger

        when:
            def result = step.processReport(reportZip, reportDirName, outDirPath, listener)

        then:
            result == extractedFiles
            loggerCalled * logger.println("[WARNING] TestReport is missing one or all log files!")

        where:
            scenario               | extractedFiles                           | loggerCalled
            "all files present"    | ["ecu.test_out.log", "ecu.test_err.log"] | 0
            "one file missing"     | ["ecu.test_out.log"]                     | 1
            "all files missing"    | []                                       | 1
    }

    def "Test DescriptorImpl returns correct values"() {
        given:
            def descriptor = new ProvideExecutionLogsStep.DescriptorImpl()

        expect:
            descriptor.functionName == 'ttProvideLogs'
            descriptor.displayName == '[TT] Provide ecu.test logs as job artifacts.'
            descriptor.requiredContext == [Launcher, EnvVars, TaskListener] as Set
    }
}
