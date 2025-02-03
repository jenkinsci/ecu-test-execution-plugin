package de.tracetronic.jenkins.plugins.ecutestexecution.model
import spock.lang.Specification

class CheckPackageResultTest extends Specification {

    def "CheckPackageResult constructor sets result correctly"() {
        expect:
            new CheckPackageResult("path/to/test", issues).result == result

        where:
            issues                                                                              | result
            null                                                                                | "ERROR"
            []                                                                                  | "SUCCESS"
            [ [filename: "file1", message: "issue1"], [filename: "file2", message: "issue2"] ]  | "ERROR"
    }

    def "toString method returns correct string representation for issues"() {
        given:
            def issues = [ [filename: "file1", message: "issue1"], [filename: "file2", message: "issue2"] ]
            def checkResult = new CheckPackageResult("path/to/test", issues)
            def str = checkResult.toString()

        expect:
            str.contains("-> result: ERROR")
            str.contains("-> 2 issues in path/to/test")
            str.contains("--> file1: issue1")
            str.contains("--> file2: issue2")
    }

    def "toString method returns correct string representation when issues are empty"() {
        given:
            def checkResult = new CheckPackageResult("path/to/test", given)

        expect:
            checkResult.toString() == expected

        where:
            given   | expected
            []      | "-> result: SUCCESS"
            null    | "-> result: ERROR"
    }
}
