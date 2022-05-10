# Migration

In the following sections an overview of the previous pipeline steps, and the new ones are given as well as a basic
example pipeline.

## Pipeline Syntax

Existing pipeline jobs can be migrated using the pipeline syntax given in the following table.

Step Name v2.x | Step Name v3.x
-------------- | ------------------
*testFolder*   | **ttRunTestFolder**
*testPackage*  | **ttRunPackage**
*testProject*  | **ttRunProject**
*startET*      | **ttStartTool**
*stopET*       | **ttStopTool**
*publishATX*   | **ttUploadReports**

Remaining steps which are available in the [current plugin version](https://github.com/jenkinsci/ecutest-plugin) are in
development and will be available in the future.

## Example pipelines

The following pipeline examples show the differences between both plugin versions. For plugin version 2.x the following
basic example pipeline is valid.

```groovy
node('windows') {
    stage('Start Tools') {
        // start ECU-TEST instance
        startET toolName: 'ECU-TEST', workspaceDir: 'workspace', settingsDir: 'settings'
    }
    stage('Test Execution') {
        // execute ECU-TEST test folder
        testFolder failFast: false, recursiveScan: true, scanMode: 'PROJECTS_ONLY', testFile: 'S:\\ample\\Path'
        // execute ECU-TEST project (e.g. multiple test packages)
        testProject testConfig: [constants: [[name: 'sample', value: '123']], tbcFile: 'sample.tbc', tcfFile: 'sample.tcf'], testFile: 'sample.prj'
        // execute single ECU-TEST package
        testPackage testConfig: [constants: [[name: 'sample', value: '\'samplevalue\'']], forceReload: true, tbcFile: '', tcfFile: ''], testFile: 'sample.pkg'
    }
    stage('Upload Reports') {
        // upload generated report to TEST-GUIDE
        publishATX 'TEST-GUIDE'
        // or publishETLogs failedOnError: true, unstableOnWarning: true
        // or publishTMS credentialsId: 'YourCredentials', toolName: 'ECU-TEST'
        // or publishTRF()
        // or publishUNIT failedThreshold: 2.0, toolName: 'ECU-TEST', unstableThreshold: 1.0
    }
    stage('Stop Tools') {
        // shutdown ECU-TEST instance
        stopET 'ECU-TEST'
    }
}
```

With plugin version 3.x the basic example pipeline can be migrated as follows:

```groovy
node('windows') {
    stage('Start Tools') {
        // start ECU-TEST instance
        ttStartTool toolName: 'ECU-TEST', workspaceDir: 'workspace', settingsDir: 'settings'
    }
    stage('Test Execution') {
        // execute ECU-TEST test folder
        ttRunTestFolder failFast: false, recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: 'S:\\ample\\Path'
        // execute ECU-TEST project (e.g. multiple test packages)
        ttRunProject testCasePath: 'sample.prj', testConfig: [tbcPath: 'sample.tbc', tcfPath: 'sample.tcf', constants: [[label: 'sample', value: '123']]]
        // execute single ECU-TEST package
        ttRunPackage testCasePath: 'sample.pkg', testConfig: [tbcPath: '', tcfPath: '', forceConfigurationReload: true, constants: [[label: 'sample', value: '\'sampleValue\'']]]
    }
    stage('Generate Reports') {
        // generate report (current available formats: ATX, EXCEL, HTML, JSON, OMR, TestSpec, TRF-SPLIT, TXT, UNIT)
        ttGenerateReports 'HTML'
    }
    stage('Upload Reports') {
        // upload generated reports into project (projectId) of TEST-GUIDE
        ttUploadReports testGuideUrl: 'http://HOST:Port', authKey: 'ApIAUTheNtiCatIOnKeY0123456789', projectId: 1
    }
    stage('Stop Tools') {
        // shutdown ECU-TEST instance
        ttStopTool 'ECU-TEST'
    }
}
```