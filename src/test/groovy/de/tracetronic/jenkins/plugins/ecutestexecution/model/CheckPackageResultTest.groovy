package de.tracetronic.jenkins.plugins.ecutestexecution.model
import spock.lang.Specification

class CheckPackageResultTest extends Specification {

    def "CheckPackageResult constructor sets result to ERROR when issues are null or empty"() {
        when:
            def resultNull = new CheckPackageResult("path/to/test", null)

        then:
            resultNull.result == "ERROR"

        when:
            def resultEmpty = new CheckPackageResult("path/to/test", [])

        then:
            resultEmpty.result == "SUCCESS"
    }

    def "CheckPackageResult constructor sets result to SUCCESS when issues are non-empty"() {
        given:
            def issues = [ [filename: "file1", message: "issue1"], [filename: "file2", message: "issue2"] ]

        when:
            def resultSuccess = new CheckPackageResult("path/to/test", issues)

        then:
            resultSuccess.result == "ERROR"
    }

    def "toString method returns correct string representation for issues"() {
        given:
            def issues = [ [filename: "file1", message: "issue1"], [filename: "file2", message: "issue2"] ]
            def checkResult = new CheckPackageResult("path/to/test", issues)

        when:
            def str = checkResult.toString()

        then:
            str.contains("-> result: ERROR")
            str.contains("-> 2 issues in path/to/test")
            str.contains("--> file1: issue1")
            str.contains("--> file2: issue2")
    }



    def "toString method returns correct string representation when issues are empty"() {
        given:
            def checkResult = new CheckPackageResult("path/to/test", [])

        when:
            def str = checkResult.toString()

        then:
            str == "-> result: SUCCESS"
    }

    def "toString method returns correct string representation when issues are null"() {
        given:
            def checkResult = new CheckPackageResult("path/to/test", null)

        when:
            def str = checkResult.toString()

        then:
            str == "-> result: ERROR"
    }
}
