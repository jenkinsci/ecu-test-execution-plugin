/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import de.tracetronic.cxs.generated.et.client.api.v2.StatusApi
import de.tracetronic.cxs.generated.et.client.api.v2.ChecksApi
import de.tracetronic.cxs.generated.et.client.api.v2.ConfigurationApi
import de.tracetronic.cxs.generated.et.client.api.v2.ExecutionApi
import de.tracetronic.cxs.generated.et.client.api.v2.ReportApi
import de.tracetronic.cxs.generated.et.client.model.v2.CheckExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.v2.CheckExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.v2.CheckFinding
import de.tracetronic.cxs.generated.et.client.model.v2.CheckReport
import de.tracetronic.cxs.generated.et.client.model.v2.ConfigurationOrder
import de.tracetronic.cxs.generated.et.client.model.v2.Execution
import de.tracetronic.cxs.generated.et.client.model.v2.ExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.v2.LabeledValue
import de.tracetronic.cxs.generated.et.client.model.v2.ReportGeneration
import de.tracetronic.cxs.generated.et.client.model.v2.ReportGenerationStatus
import de.tracetronic.cxs.generated.et.client.model.v2.SimpleMessage
import de.tracetronic.cxs.generated.et.client.model.v2.TGUpload
import de.tracetronic.cxs.generated.et.client.model.v2.TGUploadStatus
import de.tracetronic.cxs.generated.et.client.model.v2.TestConfiguration
import de.tracetronic.cxs.generated.et.client.model.v2.TestbenchConfiguration
import de.tracetronic.cxs.generated.et.client.v2.ApiClient
import de.tracetronic.cxs.generated.et.client.v2.ApiResponse
import de.tracetronic.cxs.generated.et.client.v2.Configuration
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportGenerationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.TGUploadOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ExecutionOrder

import java.util.concurrent.TimeoutException

class RestApiClientV2 implements RestApiClient{

    private ApiClient apiClient

    RestApiClientV2(String hostName, String port) {
        apiClient = Configuration.getDefaultApiClient()
        apiClient.setBasePath(String.format('http://%s:%s/api/v2', hostName, port))
    }

    /**
     * Waits until the ecu.test API is alive or timeout is reached. It uses the api "StatusApi" to get a simple ping
     * @param timeout time in seconds to wait for alive check
     * @return boolean:
     *   true, if the the ecu.test API sends an alive signal within the timeout range
     *   false, otherwise
     */
    boolean waitForAlive(int timeout = 60) {
        StatusApi statusApi = new StatusApi(apiClient)

        boolean alive = false
        long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
        while (System.currentTimeMillis() < endTimeMillis) {
            try {
                alive = statusApi.isAlive().message == 'Alive'
                if (alive) {
                    break
                }
            } catch (de.tracetronic.cxs.generated.et.client.v2.ApiException ignored) {
                sleep(1000)
            }
        }
        return alive
    }

    /**
     * Waits until the ecu.test is idle by using the api "StatusApi". Also returns if the timeout is reached
     * @param timeout time in seconds to wait for alive check
     * @return boolean:
     *   true, if the the ecu.test API sends an alive signal within the timeout range
     *   false, otherwise
     */
    boolean waitForIdle(int timeout = 60) {
        StatusApi statusApi = new StatusApi(apiClient)

        boolean idle = false
        long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
        while (timeout == 0 || System.currentTimeMillis() < endTimeMillis) {
            idle = statusApi.ecutestIsIdle().getIsIdle()
            if (idle) {
                break
            }
            sleep(1000)
        }
        return idle
    }

    /**
     * This method performs the package check for the given test package or project. It creates a check execution order
     * to get the execution ID and execute the package check for this ID.
     * It calls waitForIdle to check whether ecu.test is idle before starting
     * @param testPkgPath the path to the package or project to be checked
     * @param timeout Time in seconds until the check package execution will be aborted
     * @return CheckPackageResult with the result of the check
     * @throws ApiException on error status codes
     * @throws TimeoutException on timeout exceeded
     */
    CheckPackageResult runPackageCheck(String testPkgPath, int timeout) throws ApiException, TimeoutException {
        long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
        if (!waitForIdle(timeout)) {
            throw new TimeoutException("Timeout: check package ${testPkgPath} waited ${timeout} seconds for ecu.test to become idle")
        }

        def issues = []

        try {
            ChecksApi apiInstance = new ChecksApi(apiClient)
            CheckExecutionOrder order = new CheckExecutionOrder().filePath(testPkgPath)
            String checkExecutionId = apiInstance.createCheckExecutionOrder(order).getCheckExecutionId()

            Closure<Boolean> checkStatus = { CheckExecutionStatus response ->
                response?.status in [null, 'WAITING', 'RUNNING']
            }

            CheckExecutionStatus checkPackageStatus
            while (checkStatus(checkPackageStatus = apiInstance.getCheckExecutionStatus(checkExecutionId))) {
                if (timeout > 0 && System.currentTimeMillis() > endTimeMillis) {
                    break
                }
                sleep(1000)
            }

            if (checkPackageStatus.status != 'FINISHED' ) {
                throw new TimeoutException("Timeout: check package '${testPkgPath}' took longer than ${timeout} seconds")
            }

            CheckReport checkReport = apiInstance.getCheckResult(checkExecutionId)
            for (CheckFinding issue : checkReport.issues) {
                def issueMap = [filename: issue.fileName, message: issue.message]
                issues.add(issueMap)
            }
            return new CheckPackageResult(testPkgPath, issues)
        } catch (de.tracetronic.cxs.generated.et.client.v2.ApiException exception) {
            throw new ApiException('An error occurred during runPackageCheck. See stacktrace below:\n' +
                    exception.getMessage())
        }
    }

    /**
     * Executes the test package or project of the given ExecutionOrder via REST api.
     * It calls waitForIdle to check whether ecu.test is idle before starting
     * @param executionOrder is an ExecutionOrder object which defines the test environment and even the test package
     *   or project
     * @param timeout Time in seconds until the test execution will be aborted
     * @return ReportInfo with report information about the test execution
     */
    ReportInfo runTest(ExecutionOrder executionOrder, int timeout) {
        long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
        if (!waitForIdle(timeout)) {
            throw new TimeoutException("Timeout: run ${executionOrder.testCasePath} waited ${timeout} seconds for ecu.test to become idle")
        }

        de.tracetronic.cxs.generated.et.client.model.v2.ExecutionOrder executionOrderV2
        executionOrderV2 = executionOrder.toExecutionOrderV2()

        List<LabeledValue> constants = new ArrayList<>()
        executionOrder.constants.each { constant ->
            constants.add(new LabeledValue().label(constant.label).value(constant.value))
        }
        ConfigurationOrder configOrder = new ConfigurationOrder()
                .tbc(new TestbenchConfiguration().tbcPath(executionOrder.tbcPath))
                .tcf(new TestConfiguration().tcfPath(executionOrder.tcfPath))
                .constants(constants)
                .action(ConfigurationOrder.ActionEnum.START)
        try {
            if (executionOrder.tbcPath != null || executionOrder.tcfPath != null) {
                ConfigurationApi configApi = new ConfigurationApi(apiClient)
                ApiResponse<SimpleMessage> status = configApi.manageConfigurationWithHttpInfo(configOrder)
                if (status.statusCode != 200) {
                    throw new ApiException("Configuration could not be loaded!")
                }
            }
            ExecutionApi executionApi = new ExecutionApi(apiClient)
            executionApi.createExecution(executionOrderV2)

            Closure<Boolean> checkStatus = { Execution execution ->
                execution?.status?.key in [null, ExecutionStatus.KeyEnum.WAITING, ExecutionStatus.KeyEnum.RUNNING]
            }

            Execution execution
            while (checkStatus(execution = executionApi.currentExecution)) {
                if (timeout > 0 && System.currentTimeMillis() > endTimeMillis) {
                    executionApi.abortExecution()
                    break
                }
                sleep(1000)
            }

            if (execution.result == null) {
                // tests are not running
                return null
            }
            return ReportInfo.fromReportInfo(execution.result)
        } catch (de.tracetronic.cxs.generated.et.client.v2.ApiException exception) {
                throw new ApiException('An error occurred during runTest. See stacktrace below:\n' +
                        exception.getMessage())
        }
    }

    /**
     * Generates a report for a given report ID. The report has the format defined by the ReportGenerationOrder
     * It calls waitForIdle to check whether ecu.test is idle before starting
     * @param reportId ID of the test execution which should be reported
     * @param order ReportGenerationOrder with the definition of the report format
     * @return GenerationResult with information about the report generation
     */
    GenerationResult generateReport(String reportId, ReportGenerationOrder order) {
        waitForIdle(0)
        de.tracetronic.cxs.generated.et.client.model.v2.ReportGenerationOrder orderV2 = order.toReportGenerationOrderV2()
        try{
            ReportApi apiInstance = new ReportApi(apiClient)
            apiInstance.createReportGeneration(reportId, orderV2)

            Closure<Boolean> checkStatus = { ReportGeneration generation ->
                generation?.status?.key in [null, ReportGenerationStatus.KeyEnum.WAITING,
                                            ReportGenerationStatus.KeyEnum.RUNNING]
            }

            ReportGeneration generation
            while (checkStatus(generation = apiInstance.getCurrentReportGeneration(reportId))) {
                sleep(1000)
            }

            return new GenerationResult(generation.status.key.name(), generation.status.message,
                    generation.result.outputDir)
        } catch (de.tracetronic.cxs.generated.et.client.v2.ApiException exception){
                throw new ApiException('An error occurred during generateReport. See stacktrace below:\n' +
                        exception.getMessage())
        }
    }

    /**
     * Uploads the report to test.guide
     * It calls waitForIdle to check whether ecu.test is idle before starting
     * @param reportId ID of the test execution which should be uploaded to test.guide
     * @param order TGUploadOrder with the definition of the test.guide instance and project
     * @return UploadResult with information about the upload
     */
    UploadResult uploadReport(String reportId, TGUploadOrder order) {
        waitForIdle(0)
        de.tracetronic.cxs.generated.et.client.model.v2.TGUploadOrder uploadOrderV2
        uploadOrderV2 = order.toTGUploadOrderV2()
        try {
            ReportApi apiInstance = new ReportApi(apiClient)
            apiInstance.createUpload(reportId, uploadOrderV2)

            Closure<Boolean> checkStatus = { TGUpload upload -> upload?.status?.key in [null, TGUploadStatus.KeyEnum.WAITING, TGUploadStatus.KeyEnum.RUNNING]
            }

            TGUpload upload
            while (checkStatus(upload = apiInstance.getCurrentUpload(reportId))) {
                sleep(1000)
            }

            if (upload.result.link) {
                return new UploadResult(upload.status.key.name(),
                        'Uploaded successfully', upload.result.link)
            }
            return new UploadResult(TGUploadStatus.KeyEnum.ERROR.name(),
                    "Report upload for ${reportId} failed", '')
        } catch (de.tracetronic.cxs.generated.et.client.v2.ApiException exception) {
            throw new ApiException('An error occurred during uploadReport. See stacktrace below:\n' +
                    exception.getMessage())
        }

    }

    /**
     * Get the IDs of all available test reports in the ecu.test instance.
     * @return List of strings with report IDs
     */
    List<String> getAllReportIds() {
        ReportApi apiInstance = new ReportApi(apiClient)
        List<de.tracetronic.cxs.generated.et.client.model.v2.ReportInfo> reports = apiInstance.getAllReports()
        return reports*.testReportId
    }
}
