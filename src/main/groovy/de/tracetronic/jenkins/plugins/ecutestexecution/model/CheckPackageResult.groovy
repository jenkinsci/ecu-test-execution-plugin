package de.tracetronic.jenkins.plugins.ecutestexecution.model

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

/**
 * Class holding the result of a package check.
 */
class CheckPackageResult implements Serializable {

    private static final long serialVersionUID = 1L

    private final String filePath
    private final List<HashMap<String, String>> issues

    /**
     * Instantiates a new [CheckPackageResult].
     *
     * @param issues
     * the issues contain hashmap with filename as key and issue message as value
     */
    CheckPackageResult(String filePath,List<HashMap<String, String>> issues) {
        this.filePath = filePath
        this.issues = issues
    }

    /**
     * @return the filepath
     */
    @Whitelisted
    String getFilePath() {
        return filePath
    }

    /**
     * @return the issues
     */
    @Whitelisted
    List<HashMap<String, String>> getIssues() {
        return issues
    }

    /**
     * Returns a string representation of the object.
     * @return package check result as string
     */
    @Override
    String toString() {
        String str = ""
        if (issues.size() != 0) {
            str += "-> result: ERROR \n"
            str += "-> ${issues.size()} issues in ${filePath} \n"
        }
        else {
            str += "-> result: SUCCESS \n"
        }
        for (issue in issues) {
            str += "--> ${issue.filename}: ${issue.message}\n"
        }
        return str.stripIndent().trim()
    }
}
