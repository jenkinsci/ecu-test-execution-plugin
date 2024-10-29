/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.client.MockRestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import de.tracetronic.jenkins.plugins.ecutestexecution.model.PackageParameter
import de.tracetronic.jenkins.plugins.ecutestexecution.model.RecordingAsSetting
import hudson.Functions
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.jvnet.hudson.test.JenkinsRule

class RunTestFolderStepIT extends IntegrationTestBase {

    @Rule
    TemporaryFolder folder = new TemporaryFolder()

    File testProject
    File subPackage
    File testPackage

    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C:\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    def 'Default config round trip'() {
        given:
            RunTestFolderStep before = new RunTestFolderStep(folder.newFolder().getAbsolutePath())
        when:
            RunTestFolderStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip'() {
        given:
            RunTestFolderStep before = new RunTestFolderStep(folder.newFolder().getAbsolutePath())
            before.setScanMode(RunTestFolderStep.ScanMode.PACKAGES_ONLY)
            before.setRecursiveScan(true)
            before.setFailFast(false)

            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            before.setTestConfig(testConfig)

            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
            before.setPackageConfig(packageConfig)

            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            RecordingAsSetting recording = new RecordingAsSetting('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            analysisConfig.setRecordings(Arrays.asList(recording))
            before.setAnalysisConfig(analysisConfig)

            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(60)
            executionConfig.setExecutePackageCheck(false)
            before.setExecutionConfig(executionConfig)
        when:
            RunTestFolderStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Snippet generator with Load Configuration'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            RunTestFolderStep step = new RunTestFolderStep('/TestFolder')
        then:
            st.assertRoundTrip(step, "ttRunTestFolder '/TestFolder'")
        when:
            step.setRecursiveScan(true)
            step.setFailFast(false)
            step.setScanMode(RunTestFolderStep.ScanMode.PROJECTS_ONLY)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder failFast: false, recursiveScan: true, " +
                "scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder'")
        when:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(false)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            step.setTestConfig(testConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder failFast: false, recursiveScan: true, " +
                    "scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
        when:
            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
            step.setPackageConfig(packageConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder failFast: false, " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
        when:
            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            RecordingAsSetting recording = new RecordingAsSetting('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            analysisConfig.setRecordings(Arrays.asList(recording))
            step.setAnalysisConfig(analysisConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder " +
                    "analysisConfig: [analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], failFast: false, " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
        when:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(0)
            executionConfig.setExecutePackageCheck(true)
            step.setExecutionConfig(executionConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder " +
                    "analysisConfig: [analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], " +
                    "executionConfig: [" +
                    "executePackageCheck: true, stopOnError: false, stopUndefinedTools: false, timeout: 0], "+
                    "failFast: false, " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [constants: [[label: 'constLabel', value: 'constValue']], " +
                    "tbcPath: 'test.tbc', tcfPath: 'test.tcf']")
    }

    def 'Snippet generator with Keep Configuration'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            RunTestFolderStep step = new RunTestFolderStep('/TestFolder')
        then:
            st.assertRoundTrip(step, "ttRunTestFolder '/TestFolder'")
        when:
            step.setRecursiveScan(true)
            step.setFailFast(false)
            step.setScanMode(RunTestFolderStep.ScanMode.PROJECTS_ONLY)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder failFast: false, recursiveScan: true, " +
                    "scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder'")
        when:
            TestConfig testConfig = new TestConfig()
            testConfig.setTbcPath('test.tbc')
            testConfig.setTcfPath('test.tcf')
            testConfig.setForceConfigurationReload(true)
            testConfig.setConstants(Arrays.asList(new Constant('constLabel', 'constValue')))
            step.setTestConfig(testConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder failFast: false, recursiveScan: true, " +
                    "scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [forceConfigurationReload: true]")
        when:
            PackageConfig packageConfig = new PackageConfig(Arrays.asList(
                    new PackageParameter('paramLabel', 'paramValue')))
            step.setPackageConfig(packageConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder failFast: false, " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [forceConfigurationReload: true]")
        when:
            AnalysisConfig analysisConfig = new AnalysisConfig()
            analysisConfig.setMapping('mappingName')
            analysisConfig.setAnalysisName('analysisName')
            RecordingAsSetting recording = new RecordingAsSetting('recording.csv')
            recording.setDeviceName('deviceName')
            recording.setFormatDetails('formatDetails')
            recording.setRecordingGroup('recordingGroup')
            analysisConfig.setRecordings(Arrays.asList(recording))
            step.setAnalysisConfig(analysisConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder " +
                    "analysisConfig: [analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], failFast: false, " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [forceConfigurationReload: true]")
        when:
            ExecutionConfig executionConfig = new ExecutionConfig()
            executionConfig.setStopOnError(false)
            executionConfig.setStopUndefinedTools(false)
            executionConfig.setTimeout(0)
            executionConfig.setExecutePackageCheck(true)
            step.setExecutionConfig(executionConfig)
        then:
            st.assertRoundTrip(step, "ttRunTestFolder " +
                    "analysisConfig: [analysisName: 'analysisName', mapping: 'mappingName', " +
                    "recordings: [[deviceName: 'deviceName', formatDetails: 'formatDetails', " +
                    "path: 'recording.csv', recordingGroup: 'recordingGroup']]], " +
                    "executionConfig: [" +
                    "executePackageCheck: true, stopOnError: false, stopUndefinedTools: false, timeout: 0], "+
                    "failFast: false, " +
                    "packageConfig: [packageParameters: [[label: 'paramLabel', value: 'paramValue']]], " +
                    "recursiveScan: true, scanMode: 'PROJECTS_ONLY', testCasePath: '/TestFolder', " +
                    "testConfig: [forceConfigurationReload: true]")
    }

    def 'Run default pipeline'() {
        given:
            setupTestFolder()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(
                    new CpsFlowDefinition(
                            "node { ttRunTestFolder '${folder.getRoot().getAbsolutePath().replace('\\', '\\\\')}' }",
                            true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Found 1 package(s)', run)
            jenkins.assertLogContains('Found 1 project(s)', run)
            // packages will be execute first
            jenkins.assertLogContains("Executing package '${testPackage.getAbsolutePath()}'", run)
    }

    def 'Run pipeline by declaring .tbc and .tcf files in testConfig'() {
        given:
            setupTestFolder()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttRunTestFolder  recursiveScan: true, " +
                            "testCasePath: '${folder.getRoot().getAbsolutePath().replace('\\', '\\\\')}'" +
                            ", testConfig: [tbcPath: 'test.tbc', tcfPath: 'test.tcf'] }",
                    true))

            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = job.scheduleBuild2(0).get()
            jenkins.assertLogContains('Found 3 package(s)', run)
            jenkins.assertLogContains('Found 3 project(s)', run)
            jenkins.assertLogContains("Executing package '${subPackage.getAbsolutePath()}'", run)
            jenkins.assertLogContains("-> With TBC=test.tbc", run)
            jenkins.assertLogContains("-> With TCF=test.tcf", run)
    }

    def 'Run pipeline by forcing configuration to reload in testConfig'() {
        given:
            setupTestFolder()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttRunTestFolder  recursiveScan: true, " +
                            "testCasePath: '${folder.getRoot().getAbsolutePath().replace('\\', '\\\\')}'" +
                            ", testConfig: [forceConfigurationReload: true] }",
                    true))

            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()

        expect:
            WorkflowRun run = job.scheduleBuild2(0).get()
            jenkins.assertLogContains('Found 3 package(s)', run)
            jenkins.assertLogContains('Found 3 project(s)', run)
            jenkins.assertLogContains("Executing package '${subPackage.getAbsolutePath()}'", run)
    }

    def 'Run recursive scan pipeline'() {
        given:
            setupTestFolder()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                    "node { ttRunTestFolder  recursiveScan: true, " +
                            "testCasePath: '${folder.getRoot().getAbsolutePath().replace('\\', '\\\\')}' }",
                    true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains('Found 3 package(s)', run)
            jenkins.assertLogContains('Found 3 project(s)', run)
            // packages in subfolder will be execute first
            jenkins.assertLogContains("Executing package '${subPackage.getAbsolutePath()}'", run)
    }

    def 'Run scan mode pipeline'() {
        given:
            setupTestFolder()
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition(
                "node { ttRunTestFolder  scanMode: 'PROJECTS_ONLY', " +
                        "testCasePath: '${folder.getRoot().getAbsolutePath().replace('\\', '\\\\')}' }",
                true))

            // assume RestApiClient is available
            GroovyMock(RestApiClientFactory, global: true)
            RestApiClientFactory.getRestApiClient() >> new MockRestApiClient()
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogNotContains('No packages found!', run)
            jenkins.assertLogNotContains('Found 1 packages(s)', run)
            jenkins.assertLogContains('Found 1 project(s)', run)
            // packages will be execute first
            jenkins.assertLogContains("Executing project '${testProject.getAbsolutePath()}'", run)
    }

    def 'Run pipeline: ecu.test folder at path does not exist'() {
        given:
            File tempDir = File.createTempDir()
            String nonExistentFolder = tempDir.getPath().replace('\\', '/') + "/foo"
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunTestFolder testCasePath: '${nonExistentFolder}' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("ecu.test folder at ${nonExistentFolder} does not exist! " +
                    "Please ensure that the path is correctly set and it refers to the desired directory.", run)
    }

    def 'Run pipeline: unsupported relative paths'() {
        given:
            String relativeFolder = "relative/path/to/folder"
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipeline')
            job.setDefinition(new CpsFlowDefinition("node { ttRunTestFolder testCasePath: '${relativeFolder}' }", true))
        expect:
            WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get())
            jenkins.assertLogContains("Unsupported relative paths for ecu.test folder '${relativeFolder}'! " +
                    "Please ensure that the path is correctly set and it refers to the desired directory. " +
                    "Consider using an absolute path instead.", run)
    }

    void setupTestFolder() {
        testProject = folder.newFile("test.prj")
        testPackage = folder.newFile("test.pkg")
        File subFolder = folder.newFolder("TestSubFolder")
        subPackage = new File(subFolder, "test.pkg")
        subPackage.createNewFile()
        File.createTempFile("test2", ".pkg", subFolder)
        File.createTempFile("test", ".prj", subFolder)
        File.createTempFile("test2", ".prj", subFolder)
    }
}


