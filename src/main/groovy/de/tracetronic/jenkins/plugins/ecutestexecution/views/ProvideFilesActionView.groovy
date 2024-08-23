package de.tracetronic.jenkins.plugins.ecutestexecution.views

import hudson.model.Action
import hudson.model.Run

class ProvideFilesActionView implements Action {
    private String runId
    private String dirName
    private String iconFileName

    ProvideFilesActionView(String runID, String dirName, String iconName) {
        this.runId = runID
        this.dirName = dirName
        this.iconFileName = iconName
    }

    @Override
    String getIconFileName() {
        return "plugin/ecu-test-execution/images/file/${iconFileName}.svg"
    }

    @Override
    String getDisplayName() {
        return dirName
    }

    @Override
    String getUrlName() {
        return dirName
    }

    Run getRun() {
        return Run.fromExternalizableId(runId)
    }

    String getDirName() {
        return dirName
    }

    /**
     * Creates and returns a map containing the relative path (minus the first folder as its always the same)
     * as key and filename as entry for each artifact.
     * This is used to group the logs for each folder in the jelly view.
     * @return Map
     */
    Map<String, Object> getLogPathMap() {
        def map = [:]

        getRun()?.artifacts?.each { artifact ->
            def parts = artifact.relativePath.split("/");
            if (parts[0] != dirName) {
                return
            }
            parts = parts.drop(1)

            def currentLevel = map;
            parts.eachWithIndex{ part,  i ->
                boolean isDirectory = (i < parts.length - 1);

                if (isDirectory) {
                    currentLevel = currentLevel.computeIfAbsent(part) { [:] }
                } else {
                    currentLevel[part] = artifact
                }
            }
        }
        return map
    }
}
