package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.FilePath
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.IgnoreIf
import spock.lang.Specification

class PathUtilTest extends Specification {

    private StepContext mockContext
    private MockFilePath mockFilePath


    def setup () {
        mockContext = Mock(StepContext.class)
        mockFilePath = Mock(MockFilePath.class)
        mockContext.get(FilePath.class) >> mockFilePath
    }

    @IgnoreIf({ os.windows })
    def 'make absolute in pipeline home windows'(String jenkinsPipelineHome, String inputString,
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

    @IgnoreIf({ os.linux })
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
    }
}

class MockFilePath {
    public String getRemote() {
        return ""
    }
}
