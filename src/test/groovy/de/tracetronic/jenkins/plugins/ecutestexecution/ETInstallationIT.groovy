/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.EnvVars
import hudson.Functions
import hudson.model.Node
import hudson.model.TaskListener
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.IgnoreIf

class ETInstallationIT extends IntegrationTestBase {
    def 'getAllETInstallationsOnNode with empty list' () {
        given:
            def mockEnvVars = Mock(EnvVars)
            def mockNode = Mock(Node)
            def mockListener = Mock(TaskListener)

        when:
            def allInstallations = ETInstallation.getAllETInstallationsOnNode(mockEnvVars, mockNode,
                    mockListener)

        then:
            allInstallations.size() == 0
    }

    def 'getExeFileOnNode with installation invalid' () {
        given:
            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            ETInstallation etInstallation = new ETInstallation(toolName, executablePath, JenkinsRule.NO_PROPERTIES)
            etDescriptor.setInstallations(etInstallation)

        when:
            etInstallation.getExeFileOnNode()

        then:
            def exception = thrown(IllegalArgumentException)
            exception.message == exceptionMessage

        where:
            toolName     | executablePath               | exceptionMessage
            'INVALID-ET' | ''                           | "Tool executable path of \'${toolName}\' is not configured for this node!"
            'INVALID-ET' | 'C:\\ecu.test\\INVALID.exe'  | "Tool executable path of \'${toolName}\': \'${executablePath}\' does not contain a tracetronic tool! " +
                                                            "Please ensure the path is a full path including the executable file extension, not a directory."
    }

    @IgnoreIf({ os.linux })
    def 'getExeFileOnNode with installation (Windows)' () {
        given:
            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            ETInstallation etInstallation = new ETInstallation(toolName, exePath, JenkinsRule.NO_PROPERTIES)
            etDescriptor.setInstallations(etInstallation)

        when:
            String executable = etInstallation.getExeFileOnNode().getAbsolutePath()

        then:
            executable == exePath

        where:
            toolName       | exePath
            'ECU-TEST'     | 'C:\\ecutest\\ECU-TEST.exe'
            'ecu.test'     | 'C:\\ecutest\\ecu.test.exe'
            'TRACE-CHECK'  | 'C:\\tracecheck\\TRACE-CHECK.exe'
            'trace.check'  | 'C:\\tracecheck\\trace.check.exe'
    }

    @IgnoreIf({ os.windows })
    def 'getExeFileOnNode with installation (Linux)' () {
        given:
            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            ETInstallation etInstallation = new ETInstallation(toolName, exePath, JenkinsRule.NO_PROPERTIES)
            etDescriptor.setInstallations(etInstallation)

        when:
            String executable = etInstallation.getExeFileOnNode().getAbsolutePath()

        then:
            executable == exePath

        where:
            toolName       | exePath
            'ECU-TEST'     | '/bin/ecutest/ecu-test'
            'ecu.test'     | '/bin/ecutest/ecu.test'
            'TRACE-CHECK'  | '/bin/tracecheck/trace-check'
            'trace.check'  | '/bin/tracecheck/trace.check'
    }

    @IgnoreIf({ os.linux })
    def 'getAllETInstallationsOnNode with installations on agent (Windows)' () {
        given:
            def mockEnvVars = Mock(EnvVars)
            mockEnvVars.expand(_) >>> ["C:\\ecutest\\ECU-TEST.exe",
                                       "C:\\ecu.test\\ecu.test.exe",
                                       "C:\\tracecheck\\TRACE-CHECK.exe",
                                       "C:\\tracecheck\\trace.check.exe"]
            def mockListener = Mock(TaskListener)
            def slave = jenkins.createSlave()

            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            etDescriptor.setInstallations(
                    new ETInstallation('ECU-TEST', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('ecu.test', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('TRACE-CHECK', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('trace.check', '', JenkinsRule.NO_PROPERTIES))

        when:
            def toolInstallations = ETInstallation.getAllETInstallationsOnNode(mockEnvVars, slave,
                    mockListener)
            def toolPaths = toolInstallations.collect {it.exeFileOnNode.getName() }

        then:
            toolInstallations.size() == 4
            toolPaths.contains("ECU-TEST.exe")
            toolPaths.contains("ecu.test.exe")
            toolPaths.contains("TRACE-CHECK.exe")
            toolPaths.contains("trace.check.exe")
    }

    @IgnoreIf({ os.windows })
    def 'getAllETInstallationsOnNode with installations on agent (Linux)' () {
        given:
            def mockEnvVars = Mock(EnvVars)
            mockEnvVars.expand(_) >>> ["/bin/ecu-test",
                                       "/bin/ecu.test",
                                       "/bin/justfortesting/trace-check",
                                       "/bin/justfortesting/trace.check"]
            def mockListener = Mock(TaskListener)
            def slave = jenkins.createSlave()

            ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                    .getDescriptorByType(ETInstallation.DescriptorImpl.class)
            etDescriptor.setInstallations(
                    new ETInstallation('ECU-TEST', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('ecu.test', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('TRACE-CHECK', '', JenkinsRule.NO_PROPERTIES),
                    new ETInstallation('trace.check', '', JenkinsRule.NO_PROPERTIES))

        when:
            def toolInstallations = ETInstallation.getAllETInstallationsOnNode(mockEnvVars, slave,
                    mockListener)
            def toolPaths = toolInstallations.collect {it.exeFileOnNode.getName() }

        then:
            toolInstallations.size() == 4
            toolPaths.contains("ecu-test")
            toolPaths.contains("ecu.test")
            toolPaths.contains("trace-check")
            toolPaths.contains("trace.check")
    }

    def 'getExeFileNames for tracetronic tools' () {
        expect:
            ETInstallation.getExeFileNames() == Functions.isWindows() ?
                    ['ECU-TEST.exe', 'TRACE-CHECK.exe', 'ecu.test.exe', 'trace.check.exe'] :
                    ['ecu-test', 'trace-check', 'ecu.test', 'trace.check']
    }
}
