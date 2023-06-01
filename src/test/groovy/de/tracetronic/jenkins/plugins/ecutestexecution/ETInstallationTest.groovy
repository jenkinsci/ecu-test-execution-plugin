package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.EnvVars
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
            mockEnvVars.expand(_) >>> ["/bin/ecutest", "/bin/justfortesting/tracecheck"]
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
            allInstallations.contains("ecutest")
            allInstallations.contains("tracecheck")
    }
}
