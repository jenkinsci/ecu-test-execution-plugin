package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.EnvVars
import hudson.model.Node
import hudson.model.TaskListener

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
}
