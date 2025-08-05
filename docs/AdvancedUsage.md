# Advanced Usage Documentation

This advanced usage documentation will provide the full specs of all steps implemented in the ecu.test execution plugin.
Additionally, further examples are provided.

| Step-Name                     | Parameters                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Return                                       |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| **ttCheckPackage**            | **testCasePath**: String - The path to the file that should be checked. Can be package or project <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | [CheckPackageResult](#checkpackageresult)    |
| **ttGenerateReports**         | **generatorName**: String - The name of the report generator to trigger, currently ATX, EXCEL, HTML, JSON, TRF-SPLIT, TXT and UNIT are supported <br/><br/> **additionalSettings**: List\<[AdditionalSetting](#additionalsetting)> - Additional settings for the chosen report generator. <br/><br/> **reportIds**: List\<String> - reportIds to generate a report for, ignore to generate all. <br/><br/>  **failOnError**: boolean - If checked, the build will be marked as failed if an error occurs during report generation.                                                                                                                                                                                                                                                                                                 | List\<[GenerationResult](#generationresult)> |
| **ttProvideLogs**             | **publishConfig**: [PublishConfig](#publishconfig) - Contains settings to adjust how logs will be provided <br/><br/> **reportIds**: List\<String> - reportIds to upload, ignore to upload all.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | /                                            |
| **ttProvideReports**          | **publishConfig**: [PublishConfig](#publishconfig) - Contains settings to adjust how reports will be provided <br/><br/> **reportIds**: List\<String> - reportIds to upload, ignore to upload all.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | /                                            |
| **ttProvideGeneratedReports** | **selectedReportTypes**: String - Comma seperated names of generated report folders that should be included. <br/><br/> **publishConfig**: [PublishConfig](#publishconfig) - Contains settings to adjust how reports will be provided <br/><br/> **reportIds**: List\<String> - reportIds to upload, ignore to upload all.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | /                                            |
| **ttProvideUnitReports**      | **unstableThreshold**: Double - Defines a percentage of failed tests to mark the pipeline as unstable. No threshold is applied if the value is empty. <br/><br/> **failedThreshold**: Double - Defines a percentage of failed tests to mark the pipeline as failed. No threshold is applied if the value is empty.<br/><br/> **publishConfig**: [PublishConfig](#publishconfig) - Contains settings to adjust how reports will be provided <br/><br/> **reportIds**: List\<String> - reportIds to upload, ignore to upload all. <br/><br/> **reportGlob**: String - Defines the glob pattern to match the jUnit reports in the test report folder.                                                                                                                                                                                 | /                                            |
| **ttRunPackage**              | **testCasePath**: String - The path to the package file that should be started. A test case file can be a package, project, project archive or analysis job. The path must either be an absolute path or a relative path to the Packages directory in the workspace. <br/><br/> **testConfig**: [TestConfig](#testconfig) - Contains settings for the ecu.test configuration. <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions  <br/><br/> **packageConfig**: [PackageConfig](#packageconfig) - Contains package parameters <br/><br/> **analysisConfig**: [AnalysisConfig](#analysisconfig) - Contains settings for analysis execution.                                                                                                                      | [TestResult](#testresult)                    |
| **ttRunProject**              | **testCasePath**: String - The path to the project file that should be started. A test case file can be a package, project, project archive or analysis job. The path must either be an absolute path or a relative path to the Packages directory in the workspace. <br/><br/> **testConfig**: [TestConfig](#testconfig) - Contains settings for the ecu.test configuration. <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions                                                                                                                                                                                                                                                                                                                                | [TestResult](#testresult)                    |
| **ttRunTestFolder**           | **testCasePath**: String - Absolute test folder path where packages/projects are located. <br/><br/> **testConfig**: [TestConfig](#testconfig) - Contains settings for the ecu.test configuration. <br/><br/> **executionConfig**: [ExecutionConfig](#executionconfig) - Contains settings to handle ecu.test executions  <br/><br/> **scanMode**: [ScanMode](#scanmode) - Defines what types of files should be run (PACKAGES_ONLY, PROJECTS_ONLY, PACKAGES_AND_PROJECTS) <br/><br/>**failFast**: boolean - The first failed package or project execution will abort the test folder execution immediately.<br/><br/> **packageConfig**: [PackageConfig](#packageconfig) - Contains package parameters <br/><br/> **analysisConfig**: [AnalysisConfig](#analysisconfig) - Contains settings for analysis execution.               | List\<[TestResult](#testresult)>             |
| **ttStartTool**               | **toolName**: String - Select a preconfigured ecu.test or trace.check installation <br/><br/> **workspaceDir**: String - ecu.test or trace.check workspace, relative to build workspace or absolute path.<br/><br/> **settingsDir**: String - ecu.test or trace.check settings directory, relative to build workspace or absolute path.<br/><br/> timeout: int - Maximum time in seconds starting and connecting to the selected tool.<br/><br/> **keepInstance**: boolean - Re-uses an already running ecu.test or trace.check instance with the currently loaded workspace instead of starting a new one.<br/><br/> stopUndefinedTools: boolean - It only has an impact if <i>Keep Previous Instance</i> is unselected.</b> Additionally, all tracetronic tools that are not defined by the Jenkins ETInstallations are stopped. | [StartToolResult](#starttoolresult)          |
| **ttStopTool**                | **toolName**: String - Select a preconfigured ecu.test or trace.check installation <br/><br/> **timeout**: int - Maximum time in seconds terminating the selected tool.<br/><br/> **stopUndefinedTools**: boolean - Additionally, all tracetronic tools that are not defined by the Jenkins ETInstallations are stopped.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | /                                            |
| **ttUploadReports**           | **testGuideUrl**: String - The URL to the test.guide instance to connect to <br/><br/> **credentialsId**: String - Credentials for test.guide REST API.  Current supported credential types are `StandardUsernamePasswordCredentials` and `StringCredentials` <br/><br/>**projectId**: int - The test.guide project ID to upload to.<br/><br/> useSettingsFromServer: boolean - Get and use upload settings from test.guide. <br/><br/> additionalSettings:List\<[AdditionalSetting](#additionalsetting)> - Additional ATX generator settings. <br/><br/> **reportIds**: List\<String> - reportIds to upload, ignore to upload all. <br/><br/>  **failOnError**: boolean - If checked, the build will be marked as failed if an error occurs during the upload.                                                                    | List\<[UploadResult](#uploadresult)>         |
| **ttUploadReports**           | **tgConfiguration**: String - Name of the test.guide installation/configuration inside jenkins (see test.guide under (jenkinsurl)/manage/configure in jenkins).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | List\<[UploadResult](#uploadresult)>         |

## Advanced Pipeline Examples

### Conditional execution based on package check results

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

### Using returned reportId to generate specific reports.

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

### Using different Agents for running and uploading Reports
Enables downstream report generation and uploads with the use of either artifacts (multiple runs) or stash (single run with multiple agents)

#### Two Pipelines using artifacts
> [!IMPORTANT]  
> - Requires [CopyArtifact Plugin](https://www.jenkins.io/doc/pipeline/steps/copyartifact/).
> - You may need to [specify projects that can copy artifacts](https://github.com/jenkinsci/copyartifact-plugin?tab=readme-ov-file#specify-projects-who-can-copy-artifacts) as well.

First Agent Run And Archive Report Pipeline:
```groovy
// job name "runPackage_firstAgent"
pipeline {
    agent {
        label 'upStreamAgent'
    }

    stages {
        stage('Run Package WS1') {
            steps{
                ttStartTool toolName: 'ecu.test', workspaceDir: '<Path_to_ws1>'
                ttRunPackage 'example.pkg'
                ttStopTool 'ecu.test'
            }

        }
        stage('Archive Report'){
            steps{
                dir('<Path_to_ws1>/TestReports') {
                    archiveArtifacts artifacts: '**/*', fingerprint: true
                }
            }

        }
        stage('Trigger Upload Pipeline'){
            steps {
                build job: 'uploadReport_secondAgent',
                        parameters: [
                                string(name: 'SOURCE_BUILD', value: "${env.BUILD_NUMBER}")
                        ],
                        wait: false
            }
        }
    }
}
```
Second Agent Downstream Report Generation, Upload:
```groovy
// job name 'uploadReport_secondAgent'
pipeline {
    agent {
        label 'downStreamAgent'
    }

    parameters {
        string(name: 'SOURCE_BUILD', defaultValue: '', description: 'Triggered from which build?')
    }

    stages {
        stage('Copy Artifacts') {
            steps {
                scripts {
                    // use latest successful build if no SOURCE_BUILD parameter is given
                    def selector = params.SOURCE_BUILD?.trim() ?
                            specific(params.SOURCE_BUILD) :
                            lastSuccessful()
                    copyArtifacts(
                            projectName: 'runPackage_firstAgent',
                            selector: selector,
                            filter: '**/*',
                            target: '<Path_to_ws2>/TestReports'
                    )
                }
            }
        }
        stage('Upload Reports'){
            steps {
                dir('Path to ws2') {
                    ttStartTool toolName: 'ecu.test', workspaceDir: '<Path_to_ws2>'
                    ttUploadReports credentialsId: 'local_tg_auth', projectId: 1, testGuideUrl: 'http://localhost:8085/', useSettingsFromServer: false
                    ttGenerateReports 'UNIT'
                    ttStopTool 'ecu.test'
                }
            }
        }
    }
}
```

#### Single Pipeline Using stash
```groovy
pipeline {
    agent none

    stages {
        stage('Upstream Run Package WS1') {
            agent {
                label 'upStreamAgent'
            }
            steps {
                dir('Path to ws1/') {
                    ttStartTool toolName: 'ecu.test', workspaceDir: '<Path_to_ws1>'
                    ttRunPackage 'example.pkg'
                    ttStopTool 'ecu.test'

                    stash includes: 'TestReports/**/*', name: 'Reports'
                }
            }
        }
        stage('Downstream Generate Upload Reports WS2'){
            agent {
                label 'downStreamAgent'
            }
            steps{
                dir('Path to ws2') {
                    unstash 'Reports'
                    ttStartTool toolName: 'ecu.test', workspaceDir: '<Path_to_ws2>'
                    ttGenerateReports 'UNIT'
                    ttUploadReports credentialsId: 'local_tg_auth', projectId: 1, testGuideUrl: 'http://localhost:8085/', useSettingsFromServer: false
                    ttStopTool 'ecu.test'
                }
            }
        }
    }
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

| Properties                                   | Default Value | Description                                                                                                                                                |
|----------------------------------------------|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **tbcPath**: String                          | null          | The relative path of the .tbc file in the Configurations directory to be started for this execution. If empty, no test bench configuration will be loaded. |
| **tcfPath**: String                          | null          | The relative path of the .tcf file in the Configurations directory to be started for this execution. If empty, no test configuration will be loaded.       |
| **forceConfigurationReload**: boolean        | false         | If true, always reload the configuration even if the same one is still active.                                                                             |
| **constants**:  List\<[Constant](#constant)> | []            | The configured global constants remain available throughout the entire test execution.                                                                     |

### Configuration Change Options

- **Load Configuration**: The TestConfiguration and/or the TestBenchConfiguration files must be explicitly set whenever a new configuration is needed. If both are empty, Test Configuration will be unloaded. Setting `forceConfigurationReload` to `true` forces a configuration reload, even if the same configuration is still active.
- **Keep Configuration**: Enable this option by not specifying the testConfig property, for example `ttRunTestPackage '<myPackageName>.pkg'` this option retains the existing configuration for continued use throughout the execution.

## PublishConfig

| Properties                 | Default Value | Description                                                                                                   |
|----------------------------|---------------|---------------------------------------------------------------------------------------------------------------|
| **timeout** : int          | 3600          | Defines the maximum execution time for publishing ecu.test artifacts in seconds. Set to 0 to disable timeout. |
| **allowMissing** : boolean | false         | If true, empty test results do not lead to build failures. Otherwise, build status will be changed to failed. |
| **keepAll** : boolean      | true          | If true, archived artifacts will be kept on executor, otherwise artifacts will be deleted.                    |

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

| Properties                      | Default Value | Description                                                                                                                                                                                                                                                                          |
|---------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **path**: String                | ""            | The path must either be an absolute path or a path relative to the workspace directory.                                                                                                                                                                                              |
| **recordingGroup**: String      | ""            | Name of the recording group the recording is to be assigned to. Only required for packages unless there is only one recording group (not used for analysis packages).                                                                                                                |
| **mappingNames**: List\<String> | []            | Names of the mapping items included in this recording. Only required for analysis packages unless there is only one recording (not used for packages).                                                                                                                               |
| **deviceName**: String          | ""            | Optional device name. Required if the recording contains multiple devices and the mapping between device names in the test configuration and device names in the recording cannot be realized by format details. See "Supported file formats" in ecu.test help for more information. |
| **formatDetails**: String       | ""            | Optional format details. Required if the recording cannot be interpreted with default format details. See "Supported file formats" in ecu.test help for more information.                                                                                                            |

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

## StartToolResult

All given properties can be read via a getter method. (e.g. `.getReportId()`)

| Properties                   | Description                     |
|------------------------------|---------------------------------|
| **installationName**: String | Name of the installation.       |
| **toolExePath**: String      | Path of the installation.       |
| **workSpaceDirPath**: String | Path to the workspace directory |
| **settingsDirPath**: String  | Path to the settings            |

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
