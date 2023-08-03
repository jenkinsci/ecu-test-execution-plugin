package de.tracetronic.jenkins.plugins.ecutestexecution.model

import de.tracetronic.cxs.generated.et.client.model.CheckFinding
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

class CheckPackageResult implements  Serializable{
    private static final long serialVersionUID = 1L

    public final String class_name
    private final String issues
    private final Integer size

    CheckPackageResult(String class_name, List<CheckFinding> issues, Integer size) {
        this.issues = issues
        this.class_name = class_name
        this.size = size
    }
    @Whitelisted
    String getClassName(){
        return class_name
    }

    @Whitelisted
    String getIssues() {
        return issues.toString()
    }

    @Whitelisted
    String getSize(){
        return size.toString()
    }

    @Override
    String toString() {
        """
        -> _class: ${class_name}
        -> issues: ${issues}
        """.stripIndent().trim()
    }
}
