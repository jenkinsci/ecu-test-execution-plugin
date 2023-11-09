package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.EnvVars
import hudson.Functions
import hudson.model.Node
import hudson.model.TaskListener
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.IgnoreIf

class ETInstallationTest extends IntegrationTestBase {
    def 'getAllExecutableNames with empty list' () {
        given:
            def mockEnvVars = Mock(EnvVars)
            def mockNode = Mock(Node)
            def mockListener = Mock(TaskListener)

        when:
            def allInstallations = ETInstallation.getAllExecutableNames(mockEnvVars, mockNode,
                    mockListener)

        then:
            allInstallations.size() == 0
    }

    def 'getExeFile with installation invalid' () {
        given:
            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            ETInstallation etInstallation = new ETInstallation(toolName, executablePath, JenkinsRule.NO_PROPERTIES)
            etDescriptor.setInstallations(etInstallation)

        when:
            etInstallation.getExeFile()

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == exceptionMessage

        where:
            toolName     | executablePath               | exceptionMessage
            'INVALID-ET' | ''                           | "Tool executable path of \'${toolName}\' is not configured for this node!"
            'INVALID-ET' | 'C:\\ECU-TEST\\INVALID.exe'  | "Tool executable path of \'${toolName}\': \'${executablePath}\' does not contain a TraceTronic tool!"
    }

    @IgnoreIf({ os.linux })
    def 'getExeFile with installation (Windows)' () {
        given:
            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            ETInstallation etInstallation = new ETInstallation(toolName, exePath, JenkinsRule.NO_PROPERTIES)
            etDescriptor.setInstallations(etInstallation)

        when:
            String executable = etInstallation.getExeFile()

        then:
            executable == exePath

        where:
            toolName = 'ECU-TEST'
            exePath = 'C:\\ECU-TEST\\ECU-TEST.exe'
    }

    @IgnoreIf({ os.windows })
    def 'getExeFile with installation (Linux)' () {
        given:
            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            ETInstallation etInstallation = new ETInstallation(toolName, exePath, JenkinsRule.NO_PROPERTIES)
            etDescriptor.setInstallations(etInstallation)

        when:
            String executable = etInstallation.getExeFile()

        then:
            executable == exePath

        where:
            toolName = 'ECU-TEST'
            exePath = '/bin/ecutest/ecu-test'
    }

    @IgnoreIf({ os.linux })
    def 'getAllExecutableNames with installations on agent (Windows)' () {
        given:
            def mockEnvVars = Mock(EnvVars)
            mockEnvVars.expand(_) >>> ["C:\\ECU-TEST\\ECU-TEST.exe", "C:\\somethingelse\\TRACE-CHECK.exe"]
            def mockListener = Mock(TaskListener)
            def slave = jenkins.createSlave()

            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            etDescriptor.setInstallations(new ETInstallation('ECU-TEST', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('TRACE-CHECK', '', JenkinsRule.NO_PROPERTIES))

        when:
            def allInstallations = ETInstallation.getAllExecutableNames(mockEnvVars, slave,
                    mockListener)

        then:
            allInstallations.size() == 2
            allInstallations.contains("ECU-TEST.exe")
            allInstallations.contains("TRACE-CHECK.exe")
    }

    @IgnoreIf({ os.windows })
    def 'getAllExecutableNames with installations on agent (Linux)' () {
        given:
            def mockEnvVars = Mock(EnvVars)
            mockEnvVars.expand(_) >>> ["/bin/ecu-test", "/bin/justfortesting/trace-check"]
            def mockListener = Mock(TaskListener)
            def slave = jenkins.createSlave()

            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            etDescriptor.setInstallations(new ETInstallation('ECU-TEST', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('TRACE-CHECK', '', JenkinsRule.NO_PROPERTIES))

        when:
            def allInstallations = ETInstallation.getAllExecutableNames(mockEnvVars, slave,
                    mockListener)

        then:
            allInstallations.size() == 2
            allInstallations.contains("ecu-test")
            allInstallations.contains("trace-check")
    }

    def 'getExeFileNames for TraceTronic tools' () {
        expect:
            ETInstallation.getExeFileNames() == Functions.isWindows() ? ['ECU-TEST.exe', 'TRACE-CHECK.exe'] :
                    ['ecu-test', 'trace-check']
    }
}
