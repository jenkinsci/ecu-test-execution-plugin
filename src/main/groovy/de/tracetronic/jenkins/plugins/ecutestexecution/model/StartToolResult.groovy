package de.tracetronic.jenkins.plugins.ecutestexecution.model

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

class StartToolResult implements Serializable {
    private final String installationName
    private final String toolExePath
    private final String workSpaceDirPath
    private final String settingsDirPath

    StartToolResult(String installationName, String toolPath, String workSpaceDirPath, String settingsDirPath ){
        this.installationName = installationName
        this.toolExePath = toolPath
        this.workSpaceDirPath = workSpaceDirPath
        this.settingsDirPath = settingsDirPath
    }

    String getResult(){
        return this.installationName != null ? "SUCCESS":"FAILED"
    }

    @Whitelisted
    getInstallationName(){
        return installationName
    }

    @Whitelisted
    getToolExePath(){
        return toolExePath
    }

    @Whitelisted
    getWorkSpaceDirPath(){
        return workSpaceDirPath
    }

    @Whitelisted
    getSettingsDirPath(){
        return settingsDirPath
    }

    @Override
    String toString() {
        """
        -> result: ${getResult()}
        -> installationName: ${installationName}
        -> toolExePath: ${toolExePath}
        -> workSpaceDirPath: ${workSpaceDirPath}
        -> settingsDirPath: ${settingsDirPath}
        """.stripIndent().trim()
    }


}
