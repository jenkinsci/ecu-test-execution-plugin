# Advanced Usage Documentation

| Step-Name         | Parameters                                                                                                                                                                                                      | Return                                       |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| ttCheckPackage    | testCasePath: String <br/> executionConfig: [ExecutionConfig](#ExecutionConfig)                                                                                                                                 | [CheckPackageResult](#CheckPackageResult)    |
| ttGenerateReports | generatorName: String <br/> additionalSettings:List\<[AdditionalSetting](#AdditionalSetting)> <br/> reportIds: List\<String>                                                                                    | List\<[GenerationResult](#GenerationResult)> |
| ttProvideLogs     | timeout: int                                                                                                                                                                                                    | /                                            |
| ttRunPackage      | testCasePath: String,<br/> packageConfig: [PackageConfig](#PackageConfig) <br/> analysisConfig: [AnalysisConfig](#AnalysisConfig)                                                                               | [TestResult](#TestResult)                    |
| ttRunProject      | testCasePath: String                                                                                                                                                                                            | [TestResult](#TestResult)                    |
| ttRunTestFolder   | testCasePath: String,<br/> packageConfig: [PackageConfig](#PackageConfig) <br/> analysisConfig: [AnalysisConfig](#AnalysisConfig)                                                                               | List<[TestResult](#TestResult)>              |
| ttStartTool       | toolName: String <br/> workspaceDir: String <br/> settingsDir: String <br/> timeout: int <br/> keepInstance: boolean <br/> stopUndefinedTools: boolean                                                          | /                                            |
| ttStopTool        | toolName: String <br/> timeout: int,<br/> stopUndefinedTools: boolean                                                                                                                                           | /                                            |
| ttUploadReports   | testGuideUrl: String <br/> credentialsId: String <br/>projectId: int <br/> useSettingsFromServer: boolean <br/> additionalSettings:List\<[AdditionalSetting](#AdditionalSetting)> <br/> reportIds: List<String> | List\<[UploadResult](#UploadResult)>         |

## Advanced Pipeline Examples

Using returned ReportIds to generate specific reports.

```groovy

node {
    def res = ttRunPackage 'test.pkg'
    ttGenerateReports generatorName: 'HTML', reportIds: [res.getReportId()]
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
| packageParameters: List\<[PackageParameter](#PackageParameter)> | []            |

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



