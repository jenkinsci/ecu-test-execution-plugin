/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.FilePath
import hudson.Launcher
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

class PathUtilTest extends Specification {

    private StepContext mockContext
    private MockFilePath mockFilePath
    private MockLauncher mockLauncher


    def setup () {
        mockFilePath = Mock(MockFilePath.class)
        mockLauncher = new MockLauncher()
        mockContext = Mock(StepContext.class)
        mockContext.get(FilePath.class) >> mockFilePath
        mockContext.get(Launcher.class) >> mockLauncher
    }

    @IgnoreIf({ os.isWindows() })
    def 'make absolute in pipeline home linux'(String jenkinsPipelineHome, String inputString,
                                                 String expectedResult) {
        given:
            mockFilePath.getRemote() >> jenkinsPipelineHome

        expect:
            PathUtil.makeAbsoluteInPipelineHome(inputString, mockContext) == expectedResult

        where:
            jenkinsPipelineHome             | inputString  | expectedResult
            "Path/To/Jenkins/Pipeline/Home" | "subdir"     | "Path/To/Jenkins/Pipeline/Home/subdir"
            "Path/To/Jenkins/Pipeline/Home" | "/subdir"    | "/subdir"
    }

    @IgnoreIf({ os.isLinux() })
    def 'make absolute in pipeline home windows'(String jenkinsPipelineHome, String inputString,
                                                 String expectedResult) {
        given:
            mockFilePath.getRemote() >> jenkinsPipelineHome

        expect:
            PathUtil.makeAbsoluteInPipelineHome(inputString, mockContext) == expectedResult

        where:
            jenkinsPipelineHome             | inputString  | expectedResult
            "C:\\Jenkins\\Pipeline\\Home"   | "subdir"     | "C:/Jenkins/Pipeline/Home/subdir"
            "C:\\Jenkins\\Pipeline\\Home"   | "C:\\subdir" | "C:/subdir"
            "C:/Jenkins/Pipeline/Home"      | "C:\\subdir" | "C:/subdir"
            "C:\\Jenkins\\Pipeline\\Home"   | "C:/subdir"  | "C:/subdir"
            "C:/Jenkins/Pipeline/Home"      | "C:/subdir"  | "C:/subdir"
    }

    def 'folder created after referenced time'() {
        given:
            def referenceTime = getCalendarTime(2023, Calendar.JANUARY, 1)

        expect:
            PathUtil.isCreationDateAfter("2023-01-02_120000", referenceTime)
    }

    def 'folder created before referenced time'() {
        expect:
            !PathUtil.isCreationDateAfter("2023-01-01_120000", referenceTime)

        where:
            referenceTime << [
                    getCalendarTime(2023, Calendar.JANUARY, 1, 12, 0, 0),
                    getCalendarTime(2023, Calendar.FEBRUARY, 1)
            ]
    }

    def "folder has invalid date format"() {
        given:
            def referenceTime = getCalendarTime(2023, Calendar.JANUARY, 1)

        expect:
            !PathUtil.isCreationDateAfter("invalid_date_format", referenceTime)
    }

    @Unroll
    def "isCreationDateAfter should return #expectedResult for folder #reportDir"() {
        given:
            def referenceTime = getCalendarTime(2023, Calendar.JANUARY, 1)

        expect:
            PathUtil.isCreationDateAfter(reportDir, referenceTime) == expectedResult

        where:
        reportDir                       | expectedResult
        "2023-01-02_120000"             | true
        "2022-12-31_235959"             | false
        "2023-01-01_000000"             | false
        "2023-01-01_000001"             | true
        "some_2023-01-02_120000_text"   | true
        "2023-01-02_120000_extra"       | true
    }

    private static class MockFilePath {
        String getRemote() {
            return ""
        }
    }
    private static class MockLauncher {
        Object getChannel() {
            return null
        }
    }

    private static long getCalendarTime(int year, int month, int day, int hour = 0, int minute = 0, int second = 0) {
        Calendar calendar = Calendar.getInstance()
        calendar.set(year, month, day, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.getTimeInMillis()
    }
}


