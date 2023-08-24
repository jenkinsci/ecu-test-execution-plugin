package de.tracetronic.jenkins.plugins.ecutestexecution.model

import de.tracetronic.cxs.generated.et.client.model.CheckFinding
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

class CheckPackageResult implements  Serializable{
    private static final long serialVersionUID = 1L

    private final List<CheckFinding> issues


    CheckPackageResult(List<CheckFinding> issues) {
        this.issues = issues
    }

    @Whitelisted
    List<CheckFinding> getIssues() {
        return issues
    }


    @Override
    String toString() {
        String str = ""
        if (issues.size() != 0){
            str += "-> result: ERROR \n"
            str += "-> size: ${issues.size()} issues\n"
        }
        else{
            str += "-> result: SUCCESS \n"
        }
        for (issue in issues){
            str += "-> ${issue.getFileName()}: ${issue.getMessage()}\n"
        }
        return str.stripIndent().trim()
    }
}
