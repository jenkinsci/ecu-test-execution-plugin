package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.builder.TestPackageBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.TestProjectBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.scan.TestPackageScanner
import de.tracetronic.jenkins.plugins.ecutestexecution.scan.TestProjectScanner
import hudson.AbortException
import spock.lang.Specification
import hudson.remoting.Channel
import hudson.model.TaskListener
import hudson.util.IOUtils
import hudson.FilePath
import hudson.Launcher
import hudson.EnvVars
import org.jenkinsci.plugins.workflow.steps.StepContext
class RunTestFolderStepTest extends Specification {
    def baseFolder = "C:\\Users\\Documents\\test\\folder"
    def envVars
    def launcher
    def channel
    def taskListener
    def logger
    def context

    void setup() {
        envVars = Mock(EnvVars)
        launcher = Mock(Launcher)
        channel = Mock(Channel)
        taskListener = Mock(TaskListener)
        logger = Mock(PrintStream)
        context = Mock(StepContext) {
            get(EnvVars) >> envVars
            get(Launcher) >> launcher
            get(TaskListener) >> taskListener
        }

        launcher.getChannel() >> channel
        taskListener.getLogger() >> logger
        envVars.expand(_) >> baseFolder
    }



    def "checkFolder validates folder existence and path"() {
        given:

            def step = new RunTestFolderStep(baseFolder)
            def execution = new RunTestFolderStep.Execution(step, context)

            GroovyMock(IOUtils, global: true)
            IOUtils.isAbsolute(_) >> true

            GroovyMock(FilePath, global: true)
            def filePath = GroovyMock(FilePath)
            new FilePath(_, _) >> filePath
            filePath.exists() >> true
            filePath.getRemote() >> "Folder"

        when:
            def result = execution.checkFolder(baseFolder)

        then:
            result == "Folder"

    }
    def "checkFolder validates folder existence and path"() {
        given:

            def step = new RunTestFolderStep(baseFolder)
            def execution = new RunTestFolderStep.Execution(step, context)

            GroovyMock(IOUtils, global: true)
            IOUtils.isAbsolute(_) >> isAbsolute

            GroovyMock(FilePath, global: true)
            def filePath = GroovyMock(FilePath)
            new FilePath(_, _) >> filePath
            filePath.exists() >> exists
            filePath.getRemote() >> "Folder"

        when:
            execution.checkFolder(baseFolder)

        then:
            thrown(AbortException)

        where:
            isAbsolute  | exists
            false       | true
            true        | false

    }

    def "scanPackages returns list of package files"() {
        given:
            def step = new RunTestFolderStep(baseFolder)
            GroovyMock(TestPackageScanner, global: true)
            def packageScanner = Mock(TestPackageScanner)
            new TestPackageScanner(_, _, _) >> packageScanner
            packageScanner.scanTestFiles() >> expectedPackages

        when:
            def result = step.scanPackages("", context, scanMode, false)

        then:
            result == expectedPackages
            expectLog * context.get(TaskListener.class).logger.println(expectedLog)

        where:
            scanMode                                        | expectedPackages              | expectLog | expectedLog
            RunTestFolderStep.ScanMode.PROJECTS_ONLY        | []                            | 0         | ""
            RunTestFolderStep.ScanMode.PACKAGES_ONLY        | []                            | 1         | "No packages found!"
            RunTestFolderStep.ScanMode.PACKAGES_ONLY        | ["package.pkg","package2.pkg"]| 1         | "Found ${expectedPackages.size()} package(s)"
            RunTestFolderStep.ScanMode.PACKAGES_AND_PROJECTS| ["package.pkg"]               | 1         | "Found ${expectedPackages.size()} package(s)"
    }

    def "scanProjects returns list of project files"() {
        given:
            def step = new RunTestFolderStep(baseFolder)
            GroovyMock(TestProjectScanner, global: true)
            def projectScanner = Mock(TestProjectScanner)
            new TestProjectScanner(_, _, _) >> projectScanner
            projectScanner.scanTestFiles() >> expectedProjects

        when:
            def result = step.scanProjects("", context, scanMode, false)

        then:
            result == expectedProjects
            expectLog * context.get(TaskListener.class).logger.println(expectedLog)

        where:
            scanMode                                        | expectedProjects              | expectLog | expectedLog
            RunTestFolderStep.ScanMode.PACKAGES_ONLY        | []                            | 0         | ""
            RunTestFolderStep.ScanMode.PROJECTS_ONLY        | []                            | 1         | "No projects found!"
            RunTestFolderStep.ScanMode.PROJECTS_ONLY        | ["package.pkg","package2.pkg"]| 1         | "Found ${expectedProjects.size()} project(s)"
            RunTestFolderStep.ScanMode.PACKAGES_AND_PROJECTS| ["package.pkg"]               | 1         | "Found ${expectedProjects.size()} project(s)"
    }

    def "run method executes tests based on failFast setting"() {
        given:
            GroovySpy(RunTestFolderStep, global: true)
            RunTestFolderStep.scanPackages(*_) >> ["package1.pkg", "package2.pkg"]
            RunTestFolderStep.scanProjects(*_) >> ["project1.prj", "project2.prj"]

            def step = Spy(RunTestFolderStep, constructorArgs: [baseFolder])
            step.isFailFast() >> failFast
            def execution = Spy(RunTestFolderStep.Execution, constructorArgs: [step, context])
            execution.checkFolder(_) >> "folder"

            def testResult = Mock(TestResult)
            testResult.getTestResult() >>> givenResults

            def testPackageBuilder = Mock(TestPackageBuilder)
            GroovyMock(TestPackageBuilder, global: true)
            new TestPackageBuilder(*_) >> testPackageBuilder
            testPackageBuilder.runTest() >> testResult

            def testProjectBuilder = Mock(TestProjectBuilder)
            GroovyMock(TestProjectBuilder, global: true)
            new TestProjectBuilder(*_) >> testProjectBuilder
            testProjectBuilder.runTest() >> testResult

        when:
            def results = execution.run()

        then:
            results.size() == expectedResultsSize
            results.contains(testResult)

        where:
            failFast | givenResults | expectedResultsSize
            true     | ['FAILED']   | 1
            false    | ['FAILED']   | 4
            true     | ['SUCCESS']  | 4
            false    | ['SUCCESS']  | 4
            true     | ['SUCCESS', 'SUCCESS', 'FAILED', 'SUCCESS']        | 3
            false    | ['SUCCESS', 'SUCCESS', 'FAILED', 'SUCCESS']        | 4
    }
}
