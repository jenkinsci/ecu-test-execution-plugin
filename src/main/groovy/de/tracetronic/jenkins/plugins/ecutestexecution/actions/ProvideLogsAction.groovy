package de.tracetronic.jenkins.plugins.ecutestexecution.actions

import hudson.model.Run
import jenkins.model.RunAction2

class ProvideLogsAction implements RunAction2 {
    private transient Run run
    private String logDirName

    ProvideLogsAction(Run run, String logDirName) {
        this.run = run
        this.logDirName = logDirName
    }

    @Override
    String getIconFileName() {
        return "clipboard.png"
    }

    @Override
    String getDisplayName() {
        return "ecu.test logs"
    }

    @Override
    String getUrlName() {
        return "et-logs"
    }

    @Override
    void onAttached(Run run) {
        this.run = run

    }

    @Override
    void onLoad(Run run) {
        this.run = run
    }

    Run getRun() {
        return run
    }

    String getLogDirName() {
        return logDirName ?: "reportLogs"
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
