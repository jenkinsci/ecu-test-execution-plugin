package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.FilePath
import hudson.Launcher
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.IgnoreIf
import spock.lang.Specification

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
}


