# Migration

In the following sections an overview of the previous pipeline steps, and the new ones are given as well as a basic
example pipeline.

## Pipeline Syntax

Existing pipeline jobs can be migrated using the pipeline syntax given in the following table.

 Step Name v2.x  | Step Name v3.x       
-----------------|----------------------
 *testFolder*    | **ttRunTestFolder**  
 *testPackage*   | **ttRunPackage**     
 *testProject*   | **ttRunProject**     
 *startET*       | **ttStartTool**      
 *stopET*        | **ttStopTool**       
 *publishATX*    | **ttUploadReports**  
 *publishETLogs* | **ttProvideLogs**    
 *publishTRF*    | **ttProvideReports** 
 *publishUNIT*   | **ttProvideUnitReports**

Remaining steps which are available in the [current plugin version](https://github.com/jenkinsci/ecutest-plugin) are in
development and will be available in the future.

## Downstream Report Generation
Still works by archiving the test run report file as artifact and using the Copy Artifact plugin to generate other 
report formats or upload the report to test.guide on a downstream agent. 

See [advanced usage docs](./docs/AdvancedUsage.md) for example pipelines.
## Example pipelines

The following pipeline examples show the differences between both plugin versions. For plugin version 2.x the following
basic example pipeline is valid.

```groovy
node('windows') {
    stage('Start Tools') {
        // start ecu.test instance
        startET toolName: 'ecu.test', workspaceDir: 'workspace', settingsDir: 'settings'
    }
    stage('Test Execution') {
        // execute ecu.test test folder
        testFolder failFast: false, recursiveScan: true, scanMode: 'PROJECTS_ONLY', testFile: 'S:\\ample\\Path'
        // execute ecu.test project (e.g. multiple test packages)
        testProject testConfig: [constants: [[name: 'sample', value: '123']], tbcFile: 'sample.tbc', tcfFile: 'sample.tcf'], testFile: 'sample.prj'
        // execute single ecu.test package
        testPackage testConfig: [constants: [[name: 'sample', value: '\'samplevalue\'']], forceReload: true, tbcFile: '', tcfFile: ''], testFile: 'sample.pkg'
    }
    stage('Upload Reports') {
        // upload generated report to test.guide
        publishATX 'test.guide'
        publishETLogs failedOnError: true, unstableOnWarning: true
        publishTRF()
        // or publishTMS credentialsId: 'YourCredentials', toolName: 'ecu.test'
        // or publishUNIT failedThreshold: 2.0, toolName: 'ecu.test', unstableThreshold: 1.0
    }
    stage('Stop Tools') {
        // shutdown ecu.test instance
        stopET 'ecu.test'
    }
}
```

With plugin version 3.x the basic example pipeline can be migrated as follows:

```groovy
node('windows') {
    stage('Start Tools') {
        // start ecu.test instance
        ttStartTool toolName: 'ecu.test', workspaceDir: 'workspace', settingsDir: 'settings'
    }
    stage('Test Execution') {
        // execute ecu.test test folder
        ttRunTestFolder failFast: false, recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: 'S:\\ample\\Path'
        // execute ecu.test project (e.g. multiple test packages)
        ttRunProject testCasePath: 'sample.prj', testConfig: [tbcPath: 'sample.tbc', tcfPath: 'sample.tcf', constants: [[label: 'sample', value: '123']]]
        // execute single ecu.test package
        ttRunPackage testCasePath: 'sample.pkg', testConfig: [tbcPath: '', tcfPath: '', forceConfigurationReload: true, constants: [[label: 'sample', value: '\'sampleValue\'']]]
    }
    stage('Generate Reports') {
        // generate report (current available formats: ATX, EXCEL, HTML, JSON, OMR, TestSpec, TRF-SPLIT, TXT, UNIT)
        ttGenerateReports 'HTML'
    }
    stage('Upload Reports') {
        // upload generated reports into project (projectId) of test.guide
        ttUploadReports credentialsId: 'tgAuthKey', projectId: 1, testGuideUrl: 'http://HOST:Port'
        ttProvideLogs()
        ttProvideReports()
    }
    stage('Stop Tools') {
        // shutdown ecu.test instance
        ttStopTool 'ECU-TEST'
    }
}
```
