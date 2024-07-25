package de.tracetronic.jenkins.plugins.ecutestexecution.views

import hudson.model.Action
import hudson.model.Run

class ProvideLogsActionView implements Action {
    private String runId
    private String logDirName

    ProvideLogsActionView(String runID, String logDirName) {
        this.runId = runID
        this.logDirName = logDirName
    }

    @Override
    String getIconFileName() {
        return "orange-square.png"
    }

    @Override
    String getDisplayName() {
        return "ecu.test logs"
    }

    @Override
    String getUrlName() {
        return "et-logs"
    }

    Run getRun() {
        return Run.fromExternalizableId(runId)
    }

    String getLogDirName() {
        return logDirName
    }

    /**
     * Creates and returns a map containing the relative path (minus the first folder as its always the same)
     * as key and filename as entry for each artifact.
     * This is used to group the logs for each folder in the jelly view.
     * @return Map
     */
    Map<String, List<String>> getLogPathMap() {
        def map = new HashMap<String, List<String>>()
        getRun()?.artifacts?.each { artifact ->
            def pathParts = artifact.relativePath.split("/")
            if (pathParts[0] == logDirName) {
                def key = pathParts[1..-2].join("/")
                map.computeIfAbsent(key) { [] }.add(pathParts[-1])
            }
        }
        return map
    }

}
