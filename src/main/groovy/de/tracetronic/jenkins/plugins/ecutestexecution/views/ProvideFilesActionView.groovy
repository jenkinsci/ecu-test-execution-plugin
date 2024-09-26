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
     * Generates a map representing the hierarchical structure of artifact paths.
     * <p>
     * This method processes the artifacts associated with the current run and organizes
     * them into a nested map structure where the keys represent directories and files.
     * Each level of the map corresponds to a directory, and the final level contains
     * the artifact object associated with the file.
     * </p>
     *
     * @return A map where the keys represent directory names or file names and the
     *         values are either nested maps (for directories) or artifact objects
     *         (for files). The map structure mirrors the directory structure of the
     *         artifacts relative to a specified directory.
     */
    Map<String, Object> getLogPathMap() {
        def map = new LinkedHashMap()

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
