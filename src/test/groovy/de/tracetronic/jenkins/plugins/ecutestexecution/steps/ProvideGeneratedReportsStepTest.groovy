package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification
import hudson.EnvVars
import hudson.Launcher
import hudson.model.TaskListener

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProvideGeneratedReportsStepTest extends Specification {
    def createTestZip(){
        def reportZip  = File.createTempFile("test", ".zip")
        reportZip.deleteOnExit()
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(reportZip))
        zos.putNextEntry(new ZipEntry("html/"))
        zos.closeEntry()
        zos.putNextEntry(new ZipEntry("html/file1.html"))
        zos.closeEntry()
        zos.putNextEntry(new ZipEntry("json/"))
        zos.closeEntry()
        zos.putNextEntry(new ZipEntry("json/file1.json"))
        zos.closeEntry()
        zos.putNextEntry(new ZipEntry("package/json/file1.json"))
        zos.closeEntry()
        zos.close()
        return reportZip
    }
    def "Test processReport #scenario"() {
            given:
                def step = new ProvideGeneratedReportsStep()
                step.setSelectedReportTypes(pattern)
                def reportDirName = "testreport"
                def outDirPath = "/tmp/output"
                def listener = Mock(TaskListener)
                def logger = Mock(PrintStream)
            and:
                listener.logger >> logger

            when:
                def result = step.processReport(createTestZip(), reportDirName, outDirPath, listener)

            then:
                result.collect { it.replaceAll("\\\\", "/") }.toSet() == extractedFiles.toSet()
                loggerCalled * logger.println("[WARNING] Could not find any matching generated report files in testreport!")

            where:
                scenario              | pattern           |extractedFiles                                                                                                   | loggerCalled
                "select one"          | "html"            |["/tmp/output/testreport/html.zip"]                                                                              | 0
                "select all"          | "html, json"      |["/tmp/output/testreport/json.zip", "/tmp/output/testreport/html.zip", "/tmp/output/testreport/package/json.zip"]| 0
                "select all no space" | "html,json"       |["/tmp/output/testreport/json.zip", "/tmp/output/testreport/html.zip", "/tmp/output/testreport/package/json.zip"]| 0
                "exclude all"         | "nothing matches" |[]                                                                                                               | 1
        }

    def "Test DescriptorImpl returns correct values"() {
        given:
            def descriptor = new ProvideGeneratedReportsStep.DescriptorImpl()
        expect:
            descriptor.functionName == 'ttProvideGeneratedReports'
            descriptor.displayName == '[TT] Provide generated ecu.test reports as job artifacts.'
            descriptor.requiredContext == [Launcher, EnvVars, TaskListener] as Set
    }
}
