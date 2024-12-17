package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import hudson.AbortException
import hudson.EnvVars
import hudson.FilePath
import hudson.Launcher
import hudson.model.TaskListener
import hudson.remoting.Channel
import hudson.util.FormValidation
import hudson.util.IOUtils
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.Specification

class RunPackageStepTest extends Specification {

    def "removeEmptyParameters filters out invalid package parameters"() {
        given:
            def validParam = new PackageParameter("validLabel", "value1")
            def invalidParam = new PackageParameter("", "value2")
            def config = new PackageConfig([validParam, invalidParam])
            def step = new RunPackageStep("testPath")
            step.setPackageConfig(config)

        expect:
            step.getPackageConfig().getPackageParameters().size() == 1
            step.getPackageConfig().getPackageParameters()[0].label == "validLabel"
    }


    def "setPackageConfig assigns default when null is passed"() {
        given:
            def step = new RunPackageStep("testPath")
            step.setPackageConfig(null)

        expect:
            step.getPackageConfig().getPackageParameters() == []
    }


    def "setAnalysisConfig assigns provided value"() {
        given:
            def config = new AnalysisConfig()
            config.setAnalysisName("My Analysis")

            def step = new RunPackageStep("testPath")
            step.setAnalysisConfig(config)

        expect:
            step.getAnalysisConfig().getAnalysisName() == "My Analysis"
    }



    def "setAnalysisConfig assigns default when null is passed"() {
        given:
            def step = new RunPackageStep("testPath")
            step.setAnalysisConfig(null)

        expect:
            step.getAnalysisConfig().getAnalysisName() == ""
            step.getAnalysisConfig().getMapping() == ""
            step.getAnalysisConfig().getRecordings().isEmpty()
    }

    def "doCheckTestCasePath should validate paths correctly with specified error message"() {
        given:
            def descriptor = new RunPackageStep.DescriptorImpl()

        when:
            def result = descriptor.doCheckTestCasePath(testCasePath)

        then:
            result.kind == expectedValidation.kind
            result.message == expectedValidation.message

        where:
            testCasePath                    || expectedValidation
            "valid/path/to/test.pkg"        || FormValidation.ok()
            "invalid/path/to/test.txt"      || FormValidation.error("invalid/path/to/test.txt has to be of file type '.pkg'")
            '${WORKSPACE}/path/to/test.prj' || FormValidation.warning('Value cannot be resolved at validation-time, be sure to allocate with a valid value.')
    }

    def "test checkProjectPath with valid and invalid project paths"() {
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
            taskListener.getLogger() >> logger

            GroovyMock(IOUtils, global: true)
            IOUtils.isAbsolute(_) >> isAbsolute

            GroovyMock(FilePath, global: true)
            def projectPath = GroovyMock(FilePath)
            new FilePath(channel, projectFile) >> projectPath
            projectPath.exists() >> pathExists
            projectPath.getRemote() >> projectFile

            def step = new RunPackageStep(projectFile)
            def execution = new RunPackageStep.Execution(step, context)

        when:
            execution.checkPackagePath(projectFile)

        then:
            noExceptionThrown()

        where:
            projectFile                      | isAbsolute | pathExists
            "/foo/bar/test.pkg"            | true       | true
    }

    def "test checkProjectPath throws AbortException for non-existent project path"() {
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
            taskListener.getLogger() >> logger

            GroovyMock(IOUtils, global: true)
            IOUtils.isAbsolute(_) >> true

            GroovyMock(FilePath, global: true)
            def projectPath = GroovyMock(FilePath)
            new FilePath(channel, projectFile) >> projectPath
            projectPath.exists() >> false
            projectPath.getRemote() >> projectFile

            def step = new RunPackageStep(projectFile)
            def execution = new RunPackageStep.Execution(step, context)

        when:
            execution.checkPackagePath(projectFile)

        then:
            def e = thrown(AbortException)
            e.message == "ecu.test package at ${projectFile} does not exist! Please ensure that the path " +
                    "is correctly set and it refers to the desired directory."

        where:
            projectFile                     | _
            "/foo/bar/test.pkg"             | _
    }

}
