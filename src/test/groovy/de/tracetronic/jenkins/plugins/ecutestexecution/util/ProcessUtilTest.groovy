/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import hudson.Functions
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class ProcessUtilTest extends Specification {

    def "Unsupported class exception"() {
        when:
            new ProcessUtil()
        then:
            def e = thrown(UnsupportedOperationException)
            e.cause == null
            e.message == "Utility class"
    }

    def 'test killProcess with timeout'() {
        given:
            GroovyMock(Functions, global: true)
            Functions.isWindows() >> false
        and:
            def mockProcess = GroovyMock(Process)
            def mockBuilder = GroovyMock(ProcessBuilder)
            GroovyMock(ProcessBuilder, global: true)
            new ProcessBuilder() >> mockBuilder
            mockBuilder.command(_) >> mockBuilder
            mockBuilder.start() >> mockProcess
        and:
            def countWaitFor = 1 - countWaitForTimeout
        when:
            def result = ProcessUtil.killProcess("doesReallyNotExistFoo", timeout)
        then:
            result
            countWaitForTimeout* mockProcess.waitFor() >> 0
            countWaitFor* mockProcess.waitFor(_, TimeUnit.SECONDS) >> true
        where:
            timeout | countWaitForTimeout
                -1  |   1
                0   |   1
                1   |   0
    }

    def 'test killProcess for different os and task name'() {
        given:
            GroovyMock(Functions, global: true)
            Functions.isWindows() >> isWindows
        and:
            def mockProcess = GroovyMock(Process)
            def mockBuilder = GroovyMock(ProcessBuilder)
            GroovyMock(ProcessBuilder, global: true)
            new ProcessBuilder() >> mockBuilder
            def withArgs
            mockBuilder.command(_) >> { args ->
                withArgs = args[0]
                mockBuilder
            }
            mockBuilder.start() >> mockProcess
            mockProcess.waitFor() >> 0
        when:
            def result = ProcessUtil.killProcess("doesReallyNotExistFoo", 0)
        then:
            result
            withArgs.contains(processName)
        where:
            isWindows << [false, true]
            processName << ['kill', 'taskkill.exe']
    }

    def 'test killProcesses with default timeout'() {
        given:
            def killProcessesCallCount = 0
            def capturedExecutables = []
            def capturedTimeout = 30 // default timeout if method not called
            def responses = killProcessReturn.iterator()
            ProcessUtil.metaClass.static.killProcess = { String executable, int timeout->
                killProcessesCallCount++
                capturedExecutables << executable
                capturedTimeout = timeout
                responses.next()
            }
        when:
            def result = ProcessUtil.killProcesses(exeFiles)
        then:
            result == expected
            killProcessesCallCount == methodCalls
            capturedExecutables == exeFiles
            capturedTimeout == 30
        cleanup:
            ProcessUtil.metaClass = null
        where:
            exeFiles                            | killProcessReturn   | methodCalls     | expected
            ['ecu.test.exe', 'trace.check.exe'] | [true, true]        | 2               | true
            ['ecu.test.exe', 'trace.check.exe'] | [false, true]       | 2               | false
            ['ecu.test.exe', 'trace.check.exe'] | [true, false]       | 2               | false
            ['ecu.test.exe']                    | [true]              | 1               | true
            ['ecu.test.exe']                    | [false]             | 1               | false
            []                                  | []                  | 0               | true
    }

    def "Test killProcesses with timeout"() {
        given:
            def killProcessesCallCount = 0
            def capturedExecutables = []
            def capturedTimeout = 0
            ProcessUtil.metaClass.static.killProcess = { String executable, int timeoutArg->
                killProcessesCallCount++
                capturedExecutables << executable
                capturedTimeout = timeoutArg
                true
            }
        when:
            def result = timeout ? ProcessUtil.killProcesses(exeFiles, timeout) : ProcessUtil.killProcesses(exeFiles)
        then:
            result
            killProcessesCallCount == 2
            capturedExecutables == exeFiles
            capturedTimeout == timeout ?: 30 // check default value for timeout
        cleanup:
            ProcessUtil.metaClass = null
        where:
            exeFiles                            | timeout
            ['ecu.test.exe', 'trace.check.exe'] | null
            ['ecu.test.exe', 'trace.check.exe'] | 60
            ['ecu.test.exe', 'trace.check.exe'] | 30

    }

    def 'test killTTProcesses arguments with default timeout'() {
        given:
            def exeFiles = ['ecu.test.exe', 'trace.check.exe']
            ETInstallation.metaClass.static.getExeFileNames = { -> exeFiles }
        and:
            def killProcessesCallCount = 0
            def capturedExecutables = []
            def capturedTimeout = 0
            ProcessUtil.metaClass.static.killProcesses = { List<String> executables, int timeout ->
                killProcessesCallCount++
                capturedExecutables = executables
                capturedTimeout = timeout
                expectedResult
            }
        when:
            def result = ProcessUtil.killTTProcesses()
        then:
            result == expectedResult
            capturedExecutables == exeFiles
            capturedTimeout == 30
            killProcessesCallCount == 1
        cleanup:
            ProcessUtil.metaClass = null
            ETInstallation.metaClass = null
        where:
            expectedResult << [true, false]
    }
}
