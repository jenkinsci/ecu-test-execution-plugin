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
            IOUtils.isAbsolute(_) >> isAbsolute

            GroovyMock(FilePath, global: true)
            def filePath = GroovyMock(FilePath)
            new FilePath(_, _) >> filePath
            filePath.exists() >> exists
            filePath.getRemote() >> "Folder"

        when:
            def result = execution.checkFolder(baseFolder)

        then:
            result == expectedResult

        where:
            isAbsolute  | exists | expectedResult
            true        | true   | "Folder"

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
            if (expectedPackages.isEmpty() && scanMode != RunTestFolderStep.ScanMode.PROJECTS_ONLY ){
                1 * context.get(TaskListener.class).logger.println('No packages found!')
            }
            else if (scanMode != RunTestFolderStep.ScanMode.PROJECTS_ONLY){
                1 * context.get(TaskListener.class).logger.println("Found ${expectedPackages.size()} package(s)")
            }

        where:
            scanMode                                    | expectedPackages
            RunTestFolderStep.ScanMode.PROJECTS_ONLY    | []
            RunTestFolderStep.ScanMode.PACKAGES_ONLY    | []
            RunTestFolderStep.ScanMode.PACKAGES_ONLY    | ["package.pkg","package2.pkg"]
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
            if (expectedProjects.isEmpty() && scanMode != RunTestFolderStep.ScanMode.PACKAGES_ONLY ){
                1 * context.get(TaskListener.class).logger.println('No projects found!')
            }
            else if (scanMode != RunTestFolderStep.ScanMode.PACKAGES_ONLY){
                1 * context.get(TaskListener.class).logger.println("Found ${expectedProjects.size()} project(s)")
            }

        where:
            scanMode                                    | expectedProjects
            RunTestFolderStep.ScanMode.PACKAGES_ONLY    | []
            RunTestFolderStep.ScanMode.PROJECTS_ONLY    | []
            RunTestFolderStep.ScanMode.PROJECTS_ONLY    | ["project1.prj","project2.prj"]
    }

    def "run method executes tests based on failFast setting"() {
        given:
            GroovySpy(RunTestFolderStep, global: true)
            RunTestFolderStep.scanPackages(*_) >> ["package1.pkg", "package2.pkg"]
            RunTestFolderStep.scanProjects(*_) >> []

            def step = Spy(RunTestFolderStep, constructorArgs: [baseFolder])
            step.isFailFast() >> failFast
            def execution = Spy(RunTestFolderStep.Execution, constructorArgs: [step, context])
            execution.checkFolder(_) >> "folder"

            def testResult = Mock(TestResult)
            testResult.getTestResult() >> result

            def testPackageBuilder = Mock(TestPackageBuilder)
            GroovyMock(TestPackageBuilder, global: true)
            new TestPackageBuilder(_, _, _, _, _, _) >> testPackageBuilder
            testPackageBuilder.runTest() >> testResult

        when:
            def results = execution.run()

        then:
            results.size() == expectedResultsSize
            results.contains(testResult)

        where:
            failFast | result    | expectedResultsSize
            true     | 'FAILED'  | 1
            false    | 'FAILED'  | 2
            true     | 'SUCCESS' | 2
            false    | 'SUCCESS' | 2
    }
}
