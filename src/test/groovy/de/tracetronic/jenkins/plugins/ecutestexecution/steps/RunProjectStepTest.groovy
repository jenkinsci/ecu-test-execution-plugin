package de.tracetronic.jenkins.plugins.ecutestexecution.steps

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

class RunProjectStepTest extends Specification {

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
    }
    def "doCheckTestCasePath should validate paths correctly with specified error message"() {
        given:
            def descriptor = new RunProjectStep.DescriptorImpl()

        when:
            def result = descriptor.doCheckTestCasePath(testCasePath)

        then:
            result.kind == expectedValidation.kind
            result.message == expectedValidation.message

        where:
        testCasePath                    || expectedValidation
        "valid/path/to/test.prj"        || FormValidation.ok()
        "invalid/path/to/test.txt"      || FormValidation.error("invalid/path/to/test.txt has to be of file type '.prj'")
        '${WORKSPACE}/path/to/test.prj' || FormValidation.warning('Value cannot be resolved at validation-time, be sure to allocate with a valid value.')
    }


    def "test checkProjectPath with valid and invalid project paths"() {
        given:
            GroovyMock(IOUtils, global: true)
            IOUtils.isAbsolute(_) >> isAbsolute

            GroovyMock(FilePath, global: true)
            def projectPath = GroovyMock(FilePath)
            new FilePath(channel, projectFile) >> projectPath
            projectPath.exists() >> pathExists
            projectPath.getRemote() >> projectFile

            def step = new RunProjectStep(projectFile)
            def execution = new RunProjectStep.Execution(step, context)

        when:
            execution.checkProjectPath(projectFile)

        then:
            noExceptionThrown()

        where:
        projectFile         | isAbsolute | pathExists
        "/foo/bar/test.prj" | true       | true
    }

    def "test checkProjectPath throws AbortException for non-existent project path"() {
        given:
            def projectFile = "/foo/bar/test.prj"
            GroovyMock(IOUtils, global: true)
            IOUtils.isAbsolute(_) >> true

            GroovyMock(FilePath, global: true)
            def projectPath = GroovyMock(FilePath)
            new FilePath(channel, projectFile) >> projectPath
            projectPath.exists() >> false
            projectPath.getRemote() >> projectFile

            def step = new RunProjectStep(projectFile)
            def execution = new RunProjectStep.Execution(step, context)

        when:
            execution.checkProjectPath(projectFile)

        then:
            def e = thrown(AbortException)
            e.message == "ecu.test project at ${projectFile} does not exist! Please ensure that the path is correctly set and it refers to the desired directory."
    }


}
