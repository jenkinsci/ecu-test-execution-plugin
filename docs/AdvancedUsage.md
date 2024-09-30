# Advanced Usage Documentation

This advanced usage documentation will provide the full specs of all steps implemented in the ecu.test execution plugin.
Additionally, further examples are provided.

| Step-Name             | Parameters                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Return                                       |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| **ttCheckPackage**    | **testCasePath**: String - The path to the file that should be checked. Can be package or project <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | [CheckPackageResult](#checkpackageresult)    |
| **ttGenerateReports** | **generatorName**: String - The name of the report generator to trigger, currently ATX, EXCEL, HTML, JSON, TRF-SPLIT, TXT and UNIT are supported <br/><br/> **additionalSettings**: List\<[AdditionalSetting](#additionalsetting)> - Additional settings for the chosen report generator. <br/><br/> **reportIds**: List\<String> - reportIds to generate a report for, ignore to generate all.                                                                                                                                                                                                                                                                                                                                                                                                                                    | List\<[GenerationResult](#generationresult)> |
| **ttProvideLogs**     | **publishConfig**: [PublishConfig](#publishconfig) - Contains settings to adjust how logs will be provided                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | /                                            |
| **ttProvideReports**  | **publishConfig**: [PublishConfig](#publishconfig) - Contains settings to adjust how reports will be provided                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | /                                            |
| **ttRunPackage**      | **testCasePath**: String - The path to the package file that should be started. A test case file can be a package, project, project archive or analysis job. The path must either be an absolute path or a relative path to the Packages directory in the workspace. <br/><br/> **testConfig**: [TestConfig](#testconfig) - Contains settings for the ecu.test configuration. <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions  <br/><br/> **packageConfig**: [PackageConfig](#packageconfig) - Contains package parameters <br/><br/> **analysisConfig**: [AnalysisConfig](#analysisconfig) - Contains settings for analysis execution.                                                                                                                      | [TestResult](#testresult)                    |
| **ttRunProject**      | **testCasePath**: String - The path to the project file that should be started. A test case file can be a package, project, project archive or analysis job. The path must either be an absolute path or a relative path to the Packages directory in the workspace. <br/><br/> **testConfig**: [TestConfig](#testconfig) - Contains settings for the ecu.test configuration. <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions                                                                                                                                                                                                                                                                                                                                | [TestResult](#testresult)                    |
| **ttRunTestFolder**   | **testCasePath**: String - Absolute test folder path where packages/projects are located. <br/><br/> **testConfig**: [TestConfig](#testconfig) - Contains settings for the ecu.test configuration. <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions  <br/><br/> **scanMode**: [ScanMode](#scanmode) - Defines what types of files should be run (PACKAGES_ONLY, PROJECTS_ONLY, PACKAGES_AND_PROJECTS) <br/><br/>**failFast**: boolean - The first failed package or project execution will abort the test folder execution immediately.<br/><br/> **packageConfig**: [PackageConfig](#packageconfig) - Contains package parameters <br/><br/> **analysisConfig**: [AnalysisConfig](#analysisconfig) - Contains settings for analysis execution.               | List<[TestResult](#testresult)>              |
| **ttStartTool**       | **toolName**: String - Select a preconfigured ecu.test or trace.check installation <br/><br/> **workspaceDir**: String - ecu.test or trace.check workspace, relative to build workspace or absolute path.<br/><br/> **settingsDir**: String - ecu.test or trace.check settings directory, relative to build workspace or absolute path.<br/><br/> timeout: int - Maximum time in seconds starting and connecting to the selected tool.<br/><br/> **keepInstance**: boolean - Re-uses an already running ecu.test or trace.check instance with the currently loaded workspace instead of starting a new one.<br/><br/> stopUndefinedTools: boolean - It only has an impact if <i>Keep Previous Instance</i> is unselected.</b> Additionally, all tracetronic tools that are not defined by the Jenkins ETInstallations are stopped. | /                                            |
| **ttStopTool**        | **toolName**: String - Select a preconfigured ecu.test or trace.check installation <br/><br/> **timeout**: int - Maximum time in seconds terminating the selected tool.<br/><br/> **stopUndefinedTools**: boolean - Additionally, all tracetronic tools that are not defined by the Jenkins ETInstallations are stopped.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | /                                            |
| **ttUploadReports**   | **testGuideUrl**: String - The URL to the test.guide instance to connect to <br/><br/> **credentialsId**: String - Credentials for test.guide REST API.<br/><br/>**projectId**: int - The test.guide project ID to upload to.<br/><br/> useSettingsFromServer: boolean - Get and use upload settings from test.guide. <br/><br/> additionalSettings:List\<[AdditionalSetting](#additionalsetting)> - Additional ATX generator settings. <br/><br/> **reportIds**: List<String> - List of reportIds to upload for, ignore to upload all.                                                                                                                                                                                                                                                                                            | List\<[UploadResult](#uploadresult)>         |

## Advanced Pipeline Examples

Conditional execution based on package check results

```groovy
node {
    def checkResult = ttCheckPackage(
            testCasePath: 'test.pkg',
            executionConfig: [timeout: 1800, stopOnError: false]
    )

    if (checkResult.getResult() == 'SUCCESS') {
        ttRunPackage(testCasePath: checkResult.getTestCasePath())
    } else {
        //Handle Failed Check
        ttProvideLogs(timeout: 120)
    }
}
```

Using returned reportId to generate specific reports.

```groovy
node {
    def testResult = ttRunPackage 'test.pkg'
    def reportId = testResult.getReportId()
    // Only generate reports for given reportIds, ignores the test.pkg run
    ttGenerateReports(
            generatorName: 'ATX',
            reportIds: [reportId]
    )

    // Only upload reports for given reportIds, ignores the test.pkg run
    /*
    def uploadResult = ttUploadReports(
            testGuideUrl: 'https://your-test-guide.url',
            credentialsId: 'serverCreds',
            projectId: 1,
            useSettingsFromServer: true,
            reportIds: reportIds
    )

    echo "Upload Result: ${uploadResult.collect { it.getUploadResult() }}"
     */
}
```

# Objects and their properties

## AnalysisConfig

| Properties                                                       | Default Value | Description                                                                                            |
|------------------------------------------------------------------|---------------|--------------------------------------------------------------------------------------------------------|
| **analysisName**: String                                         | ''            | Name of the analysis to be executed.                                                                   |
| **mapping**: String                                              | ''            | Optional mapping which overwrites the default mapping. Will only be considered for analysis execution. |
| **recordings**: List\<[RecordingAsSetting](#recordingassetting)> | []            | Recordings for analysis execution. Will only be considered for analysis execution.                     |

## ExecutionConfig

| Properties                       | Default Value | Description                                                                                                                                                                   |
|----------------------------------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **timeout**: int                 | 3600          | Defines the maximum execution time in seconds running this test case. Set to 0 to disable timeout.                                                                            |
| **stopOnError**: boolean         | true          | If test execution fails, stop running ecu.test/trace.check instances. Additionally, if ttCheckPackage is also selected the execution will be skipped on package check errors. |
| **stopUndefinedTools**: boolean  | true          | It only has an impact if Stop Tools on Error is also selected. Additionally, all tracetronic tools that are not defined by the Jenkins ETInstallations are stopped.           |
| **executePackageCheck**: boolean | false         | Perform the ttCheckPackage step before execution of package or project                                                                                                        |

## PackageConfig

| Properties                                                          | Default Value | Description                       |
|---------------------------------------------------------------------|---------------|-----------------------------------|
| **packageParameters**: List\<[PackageParameter](#packageparameter)> | []            | Parameters for package execution. |

## TestConfig

| Properties                                  | Default Value | Description                                                                                                                                                                                                                 |
|---------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **tbcPath**: String                         | ''            | The relative path of the .tbc file in the Configurations directory to be started for this execution. Use "KEEP" to use the currently loaded test bench configuration. If empty, no test bench configuration will be loaded. |
| **tcfPath**: String                         | ''            | The relative path of the .tcf file in the Configurations directory to be started for this execution. Use "KEEP" to use the currently loaded test configuration. If empty, no test configuration will be loaded.             |
| **forceConfigurationReload**: boolean       | false         | If true, always reload the configuration even if the same one is still active. Hint: This flag is only required for ecu.test versions less than 2023.4!                                                                     |
| **constants**:  List<[Constant](#constant)> | []            | The configured global constants remain available throughout the entire test execution.                                                                                                                                      |

## PublishConfig

| Properties       | Default Value | Description                                                                                                   |
|------------------|---------------|---------------------------------------------------------------------------------------------------------------|
| **timeout**      | 3600          | Defines the maximum execution time for publishing ecu.test artifacts in seconds. Set to 0 to disable timeout. |
| **allowMissing** | false         | If true, empty test results do not lead to build failures. Otherwise, build status will be changed to failed. |
| **keepAll**      | true          | If true, archived artifacts will be kept on executor, otherwise artifacts will be deleted.                    |

## AdditionalSetting

| Properties        | Default Value |
|-------------------|---------------|
| **name**: String  | /             |
| **value**: String | /             |

## Constant

| Properties        | Default Value |
|-------------------|---------------|
| **label**: String | /             |
| **value**: String | /             |

## PackageParameter

| Properties        | Default Value |
|-------------------|---------------|
| **label**: String | /             |
| **value**: String | /             |

## RecordingAsSetting

| Properties                     | Default Value | Description                                                                                                                                                                                                                                                                          |
|--------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **path**: String               | ""            | The path must either be an absolute path or a path relative to the workspace directory.                                                                                                                                                                                              |
| **recordingGroup**: String     | ""            | Name of the recording group the recording is to be assigned to. Only required for packages unless there is only one recording group (not used for analysis packages).                                                                                                                |
| **mappingNames**: List<String> | []            | Names of the mapping items included in this recording. Only required for analysis packages unless there is only one recording (not used for packages).                                                                                                                               |
| **deviceName**: String         | ""            | Optional device name. Required if the recording contains multiple devices and the mapping between device names in the test configuration and device names in the recording cannot be realized by format details. See "Supported file formats" in ecu.test help for more information. |
| **formatDetails**: String      | ""            | Optional format details. Required if the recording cannot be interpreted with default format details. See "Supported file formats" in ecu.test help for more information.                                                                                                            |

## CheckPackageResult

All given properties can be read via a getter method. (e.g. `.getResult()`)

| Properties                                | Description                                                                            |
|-------------------------------------------|----------------------------------------------------------------------------------------|
| **result**: String                        | Result of the package/project check execution <br/> Can either be 'ERROR' or 'SUCCESS' |
| **testCasePath**: String                  | Path to the file that should was checked. Can be package or project                    |
| **issues**: List\<HashMap\<String,String> | List of problems found in the package check execution                                  |

## GenerationResult

All given properties can be read via a getter method. (e.g. `.getGenerationResult()`)

| Properties                    | Description                                                                         |
|-------------------------------|-------------------------------------------------------------------------------------|
| **generationResult**: String  | Result of the report generation execution <br/> Can either be 'FINISHED' or 'ERROR' |
| **generationMessage**: String | ecu.test report generation message                                                  |
| **reportOutputDir**: String   | location of ecu.test report                                                         |

## TestResult

All given properties can be read via a getter method. (e.g. `.getReportId()`)

| Properties             | Description                                                                                  |
|------------------------|----------------------------------------------------------------------------------------------|
| **reportId**: String   | reportId given by ecu.test                                                                   |
| **testResult**: String | Result of the execution <br/> Can be 'NONE', 'SUCCESS', 'INCONCLUSIVE', 'FAILED' and 'ERROR' |
| **reportDir**: String  | location of the report                                                                       |

## UploadResult

All given properties can be read via a getter method. (e.g. `.getUploadResult()`)

| Properties                | Description                                                                         |
|---------------------------|-------------------------------------------------------------------------------------|
| **uploadResult**: String  | Result of the report upload to test guide <br/> Can either be 'FINISHED' or 'ERROR' |
| **uploadMessage**: String | Response message from test.guide                                                    |
| **reportLink**: String    | test guide link to the report. Will be empty if the upload was unsuccessful         |

## ScanMode

enum **ScanMode**

| Possible Values         | Description                                                         |
|-------------------------|---------------------------------------------------------------------|
| "PACKAGES_ONLY"         | Only package files (.pkg) within this folder will be found/executed |
| "PROJECTS_ONLY"         | Only project files (.prj) within this folder will be found/executed |
| "PACKAGES_AND_PROJECTS" | Both package and project files will be found/executed               |
