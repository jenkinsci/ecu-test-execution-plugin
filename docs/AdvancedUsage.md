# Advanced Usage Documentation

| Step-Name         | Parameters                                                                                                                                                                                                      | Return                                       |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| ttCheckPackage    | testCasePath: String <br/> executionConfig: [ExecutionConfig](#executionconfig)                                                                                                                                 | [CheckPackageResult](#checkpackageresult)    |
| ttGenerateReports | generatorName: String <br/> additionalSettings:List\<[AdditionalSetting](#additionalsetting)> <br/> reportIds: List\<String>                                                                                    | List\<[GenerationResult](#generationresult)> |
| ttProvideLogs     | timeout: int                                                                                                                                                                                                    | /                                            |
| ttRunPackage      | testCasePath: String,<br/> packageConfig: [PackageConfig](#packageconfig) <br/> analysisConfig: [AnalysisConfig](#analysisconfig)                                                                               | [TestResult](#testresult)                    |
| ttRunProject      | testCasePath: String                                                                                                                                                                                            | [TestResult](#testresult)                    |
| ttRunTestFolder   | testCasePath: String, <br/>scanMode: [ScanMode](#scanmode), <br/>failFast: boolean,<br/> packageConfig: [PackageConfig](#packageconfig) <br/> analysisConfig: [AnalysisConfig](#analysisconfig)                 | List<[TestResult](#testresult)>              |
| ttStartTool       | toolName: String <br/> workspaceDir: String <br/> settingsDir: String <br/> timeout: int <br/> keepInstance: boolean <br/> stopUndefinedTools: boolean                                                          | /                                            |
| ttStopTool        | toolName: String <br/> timeout: int,<br/> stopUndefinedTools: boolean                                                                                                                                           | /                                            |
| ttUploadReports   | testGuideUrl: String <br/> credentialsId: String <br/>projectId: int <br/> useSettingsFromServer: boolean <br/> additionalSettings:List\<[AdditionalSetting](#additionalsetting)> <br/> reportIds: List<String> | List\<[UploadResult](#uploadresult)>         |

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
            testGuideUrl: '',
            credentialsId: 'serverCreds',
            projectId: 1,
            useSettingsFromServer: true,
            reportIds: reportIds
    )

    echo "Upload Result: ${uploadResult.collect { it.getUploadResult() }}"
     */
}
```

# Input Objects and their properties

## ExecutionConfig

| Properties                   | Default Value |
|------------------------------|---------------|
| timeout: int                 | 3600          |
| stopOnError: boolean         | true          |
| stopUndefinedTools: boolean  | true          |
| executePackageCheck: boolean | false         |

## PackageConfig

| Properties                                                      | Default Value |
|-----------------------------------------------------------------|---------------|
| packageParameters: List\<[PackageParameter](#packageparameter)> | []            |

## PackageParameter

| Properties    | Default Value |
|---------------|---------------|
| label: String | /             |
| value: String | /             |

## AdditionalSetting

| Properties    | Default Value |
|---------------|---------------|
| name: String  | /             |
| value: String | /             |

## AnalysisConfig

| Properties                            | Default Value |
|---------------------------------------|---------------|
| analysisName: String                  | ''            |
| mapping: String                       | ''            |
| recordings: List\<RecordingAsSetting> | []            |

## ScanMode

```groovy
enum ScanMode {
    /**
     * Scan packages only.
     */
    PACKAGES_ONLY,

    /**
     * Scan projects only.
     */
    PROJECTS_ONLY,

    /**
     * Scan both packages and projects.
     */
    PACKAGES_AND_PROJECTS
}
```

# Return Objects and their properties

## CheckPackageResult

All given properties can be read via a getter method. (e.g. `.getResult()`)

| Properties                            |
|---------------------------------------|
| result: String                        |
| testCasePath: String                  |
| issues: List\<HashMap\<String,String> |

## GenerationResult

All given properties can be read via a getter method. (e.g. `.getGenerationResult()`)

| Properties                |
|---------------------------|
| generationResult: String  |
| generationMessage: String |
| reportOutputDir: String   |

## TestResult

All given properties can be read via a getter method. (e.g. `.getReportId()`)

| Properties         |
|--------------------|
| reportId: String   |
| testResult: String |
| reportDir: String  |

## UploadResult

All given properties can be read via a getter method. (e.g. `.getUploadResult()`)

| Properties            |
|-----------------------|
| uploadResult: String  |
| uploadMessage: String |
| reportLink: String    |



