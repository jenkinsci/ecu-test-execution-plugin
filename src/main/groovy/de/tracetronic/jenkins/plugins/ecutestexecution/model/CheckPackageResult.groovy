package de.tracetronic.jenkins.plugins.ecutestexecution.model

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

/**
 * Class holding the result of a package check.
 */
class CheckPackageResult implements Serializable {

    private static final long serialVersionUID = 1L

    private final String result
    private final String testCasePath
    private final List<HashMap<String, String>> issues

    /**
     * Instantiates a new [CheckPackageResult].
     * @param testCasePath
     * the path to the file (package or project) where CheckPackageStep was executed on
     * @param issues
     * the issues contain hashmaps with filename as key and issue message as value
     */
    CheckPackageResult(String testCasePath, List<HashMap<String, String>> issues) {
        this.result = (issues == null || issues.size()) ? "ERROR" : "SUCCESS"
        this.testCasePath = testCasePath
        this.issues = issues
    }

    /**
     * @return result
     */
    @Whitelisted
    String getResult() {
        return result
    }

    /**
     * @return the testCasePath
     */
    @Whitelisted
    String getTestCasePath() {
        return testCasePath
    }

    /**
     * @return the issues
     */
    @Whitelisted
    List<HashMap<String, String>> getIssues() {
        return new ArrayList<HashMap<String, String>>(issues)
    }

    /**
     * Returns a string representation of the object.
     * @return package check result as string
     */
    @Override
    String toString() {
        String str = "-> result: ${result} \n"
        if (issues && issues.size() != 0) {
            str += "-> ${issues.size()} issues in ${testCasePath} \n"
        }
        for (issue in issues) {
            str += "--> ${issue.filename}: ${issue.message}\n"
        }
        return str.stripIndent().trim()
    }
}
