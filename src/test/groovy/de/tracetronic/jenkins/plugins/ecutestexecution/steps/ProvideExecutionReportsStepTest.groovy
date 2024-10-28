package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import spock.lang.Specification
import hudson.EnvVars
import hudson.Launcher
import hudson.model.TaskListener

class ProvideExecutionReportsStepTest extends Specification {
    def "Test processReport handles #scenario"() {
        given:
            def step = new ProvideExecutionReportsStep()
            def reportZip = GroovyMock(File)
            def reportDirName = "TestReport"
            def outDirPath = "/tmp/output"
            def listener = Mock(TaskListener)
            def logger = Mock(PrintStream)
            def outputFile = Mock(File)
            def parentFile = Mock(File)

        and:
            GroovyMock(ZipUtil, global: true)
            GroovyMock(File, global: true)
            ZipUtil.containsFileOfType(_ , ".prf") >> containsPrf
            ZipUtil.recreateZipWithFilteredFiles(_, [".trf", ".prf"], _) >> expectedResult[0]
            ZipUtil.extractFilesByExtension(_, [".trf"], _) >> expectedResult
            listener.logger >> logger
            new File(_) >> outputFile
            outputFile.parentFile >> parentFile

        when:
            def result = step.processReport(reportZip, reportDirName, outDirPath, listener)

        then:
            result == expectedResult
            loggerCalled * logger.println("[WARNING] Could not find any report files in ${reportDirName}!")

        where:
            scenario                    | containsPrf | expectedResult                            | loggerCalled
            "contains .prf file"        | true        | ["/tmp/output/TestReport/TestReport.zip"] | 0
            "contains only .trf files"  | false       | ["/tmp/output/TestReport/report1.trf"]    | 0
            "contains no report files"  | false       | []                                        | 1
    }

    def "Test DescriptorImpl returns correct values"() {
        given:
            def descriptor = new ProvideExecutionReportsStep.DescriptorImpl()

        expect:
            descriptor.functionName == 'ttProvideReports'
            descriptor.displayName == '[TT] Provide ecu.test reports as job artifacts.'
            descriptor.requiredContext == [Launcher, EnvVars, TaskListener] as Set
    }
}
