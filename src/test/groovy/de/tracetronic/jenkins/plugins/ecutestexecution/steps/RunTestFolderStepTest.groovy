package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.builder.TestPackageBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.scan.TestPackageScanner
import de.tracetronic.jenkins.plugins.ecutestexecution.scan.TestProjectScanner
import spock.lang.Specification
import hudson.remoting.Channel
import hudson.model.TaskListener
import hudson.util.IOUtils
import hudson.FilePath
import hudson.Launcher
import hudson.EnvVars
import org.jenkinsci.plugins.workflow.steps.StepContext
class RunTestFolderStepTest extends Specification {

    def "removeEmptyParameters filters out invalid package parameters"() {
        given:
        def validParam = new PackageParameter("validLabel", "value1")
        def invalidParam = new PackageParameter("", "value2")
        def config = new PackageConfig([validParam, invalidParam])
        def step = new RunTestFolderStep("testPath")
        step.setPackageConfig(config)

        expect:
        step.getPackageConfig().getPackageParameters().size() == 1
        step.getPackageConfig().getPackageParameters()[0].label == "validLabel"
    }


    def "setPackageConfig assigns default when null is passed"() {
        given:
        def step = new RunTestFolderStep("testPath")
        step.setPackageConfig(null)

        expect:
        step.getPackageConfig().getPackageParameters() == []
    }

    def "setAnalysisConfig assigns provided value"() {
        given:
        def config = new AnalysisConfig()
        config.setAnalysisName("My Analysis")

        def step = new RunTestFolderStep("testPath")
        step.setAnalysisConfig(config)

        expect:
        step.getAnalysisConfig().getAnalysisName() == "My Analysis"
    }

    def "setAnalysisConfig assigns default when null is passed"() {
        given:
        def step = new RunTestFolderStep("testPath")
        step.setAnalysisConfig(null)

        expect:
        step.getAnalysisConfig().getAnalysisName() == ""
        step.getAnalysisConfig().getMapping() == ""
        step.getAnalysisConfig().getRecordings().isEmpty()
    }

    def "test run method with different failFast and result for pkg"() {
        given:
        def envVars = Mock(EnvVars)
        def launcher = Mock(Launcher)
        def channel = Mock(Channel)
        def taskListener = Mock(TaskListener)
        def logger = Mock(PrintStream)

        def context = Mock(StepContext) {
            get(EnvVars) >> envVars
            get(Launcher) >> launcher
            get(TaskListener) >> taskListener
        }

        launcher.getChannel() >> channel
        envVars.expand(_) >> "C:\\Users\\Documents\\test\\folder"
        taskListener.getLogger() >> logger

        def step = new RunTestFolderStep("C:\\Users\\Documents\\test\\folder")
        step.failFast = failFast

        GroovyMock(IOUtils, global: true)
        IOUtils.isAbsolute(_) >> true

        GroovyMock(FilePath, global: true)
        def filePath = GroovyMock(FilePath)
        new FilePath(_, _) >> filePath
        filePath.exists() >> true
        filePath.getRemote() >> "C:\\Users\\Documents\\test\\folder"

        def testResult = Mock(TestResult)
        testResult.getTestResult() >> result

        GroovyMock(TestPackageScanner, global: true)
        def packageScanner = Mock(TestPackageScanner)
        new TestPackageScanner(_, _, _) >> packageScanner
        packageScanner.scanTestFiles() >> ["package1.pkg"]

        GroovyMock(TestProjectScanner, global: true)
        def projectScanner = Mock(TestProjectScanner)
        new TestProjectScanner(_, _, _) >> projectScanner
        projectScanner.scanTestFiles() >> []

        def testPackageBuilder = Mock(TestPackageBuilder)
        GroovyMock(TestPackageBuilder, global: true)
        new TestPackageBuilder(_, _, _, _, _, _) >> testPackageBuilder
        testPackageBuilder.runTest() >> testResult

        def execution = new RunTestFolderStep.Execution(step, context)

        when:
        def results = execution.run()

        then:
        1 * testResult.getTestResult()
        results.size() == expectedResultsSize
        results.contains(testResult)

        where:
        failFast | result    | expectedResultsSize
        true     | 'FAILED'  | 1
        false    | 'FAILED'  | 1
        true     | 'SUCCESS' | 1
        false    | 'SUCCESS' | 1
    }

    def "test run method with different failFast and result for prj"() {
        given:
        def envVars = Mock(EnvVars)
        def launcher = Mock(Launcher)
        def channel = Mock(Channel)
        def taskListener = Mock(TaskListener)
        def logger = Mock(PrintStream)

        def context = Mock(StepContext) {
            get(EnvVars) >> envVars
            get(Launcher) >> launcher
            get(TaskListener) >> taskListener
        }

        launcher.getChannel() >> channel
        envVars.expand(_) >> "C:\\Users\\Documents\\test\\folder"
        taskListener.getLogger() >> logger

        def step = new RunTestFolderStep("C:\\Users\\Documents\\test\\folder")
        step.failFast = failFast

        GroovyMock(IOUtils, global: true)
        IOUtils.isAbsolute(_) >> true

        GroovyMock(FilePath, global: true)
        def filePath = GroovyMock(FilePath)
        new FilePath(_, _) >> filePath
        filePath.exists() >> true
        filePath.getRemote() >> "C:\\Users\\Documents\\test\\folder"

        def testResult = Mock(TestResult)
        testResult.getTestResult() >> result

        GroovyMock(TestPackageScanner, global: true)
        def packageScanner = Mock(TestPackageScanner)
        new TestPackageScanner(_, _, _) >> packageScanner
        packageScanner.scanTestFiles() >> ["pro1.prj"]

        GroovyMock(TestProjectScanner, global: true)
        def projectScanner = Mock(TestProjectScanner)
        new TestProjectScanner(_, _, _) >> projectScanner
        projectScanner.scanTestFiles() >> []

        def testPackageBuilder = Mock(TestPackageBuilder)
        GroovyMock(TestPackageBuilder, global: true)
        new TestPackageBuilder(_, _, _, _, _, _) >> testPackageBuilder
        testPackageBuilder.runTest() >> testResult

        def execution = new RunTestFolderStep.Execution(step, context)

        when:
        def results = execution.run()

        then:
        1 * testResult.getTestResult()
        results.size() == expectedResultsSize
        results.contains(testResult)

        where:
        failFast | result    | expectedResultsSize
        true     | 'FAILED'  | 1
        false    | 'FAILED'  | 1
        true     | 'SUCCESS' | 1
        false    | 'SUCCESS' | 1
    }
}
