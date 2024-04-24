/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import de.tracetronic.cxs.generated.et.client.api.v1.ApiStatusApi
import de.tracetronic.cxs.generated.et.client.api.v1.ChecksApi
import de.tracetronic.cxs.generated.et.client.api.v1.ExecutionApi
import de.tracetronic.cxs.generated.et.client.api.v1.ReportApi
import de.tracetronic.cxs.generated.et.client.model.v1.CheckExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.v1.CheckExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.v1.CheckFinding
import de.tracetronic.cxs.generated.et.client.model.v1.CheckReport
import de.tracetronic.cxs.generated.et.client.model.v1.Execution
import de.tracetronic.cxs.generated.et.client.model.v1.ExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.v1.ReportGeneration
import de.tracetronic.cxs.generated.et.client.model.v1.ReportGenerationStatus
import de.tracetronic.cxs.generated.et.client.model.v1.TGUpload
import de.tracetronic.cxs.generated.et.client.model.v1.TGUploadStatus
import de.tracetronic.cxs.generated.et.client.v1.ApiClient
import de.tracetronic.cxs.generated.et.client.v1.Configuration
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportGenerationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.TGUploadOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult

import java.util.concurrent.TimeoutException

class RestApiClientV1 implements RestApiClient {

    private ApiClient apiClient
    private boolean timeoutExceeded = false

    RestApiClientV1(String hostName, String port) {
        apiClient = Configuration.getDefaultApiClient()
        apiClient.setBasePath(String.format('http://%s:%s/api/v1', hostName, port))
    }

    /**
     * Sets the timeoutExceeded to true, which will stop the execution at the next check
     */
    void setTimeoutExceeded() {
        timeoutExceeded = true
    }

    /**
     * Waits until the ecu.test API is alive or timeout is reached. It uses the api "apiStatus" to get a simple ping
     * @param timeout time in seconds to wait for alive check
     * @return boolean:
     *   true, if the the ecu.test API sends an alive signal within the timeout range
     *   false, otherwise
     * @throws TimeoutException if the execution time exceeded the timeout
     */
    boolean waitForAlive(int timeout = 60) throws TimeoutException {
        ApiStatusApi statusApi = new ApiStatusApi(apiClient)

        boolean alive = false
        long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
        while (System.currentTimeMillis() < endTimeMillis && !timeoutExceeded) {
            try {
                alive = statusApi.isAlive().message == 'Alive'
                if (alive) {
                    break
                }
            } catch (de.tracetronic.cxs.generated.et.client.v1.ApiException ignored) {
                sleep(1000)
            }
        }
        if (timeoutExceeded) {
            throw new TimeoutException("Could not find a ecu.test REST api for host: ${apiClient.getBasePath()}")
        }
        return alive
    }

    /**
     * This method performs the package check for the given test package or project. It creates a check execution order
     * to get the execution ID and execute the package check for this ID.
     * @param testPkgPath the path to the package or project to be checked
     * @return CheckPackageResult with the result of the check
     * @throws ApiException on error status codes
     * @throws TimeoutException if the execution time exceeded the timeout
     */
    CheckPackageResult runPackageCheck(String testPkgPath) throws ApiException, TimeoutException {
        def issues = []
        try {
            ChecksApi apiInstance = new ChecksApi(apiClient)
            CheckExecutionOrder order = new CheckExecutionOrder().filePath(testPkgPath)
            String checkExecutionId = apiInstance.createCheckExecutionOrder(order).getCheckExecutionId()

            Closure<Boolean> checkStatus = { CheckExecutionStatus response ->
                response?.status in [null, 'WAITING', 'RUNNING']
            }

            while (!timeoutExceeded && checkStatus(apiInstance.getCheckExecutionStatus(checkExecutionId))) {
                sleep(1000)
            }
            if (timeoutExceeded) {
                throw new TimeoutException("Timeout exceeded during: runPackageCheck '${testPkgPath}'")
            }


            CheckReport checkReport = apiInstance.getCheckResult(checkExecutionId)
            for (CheckFinding issue : checkReport.issues) {
                def issueMap = [filename: issue.fileName, message: issue.message]
                issues.add(issueMap)
            }
        } catch (de.tracetronic.cxs.generated.et.client.v1.ApiException rethrow) {
            throw new ApiException('An error occurs during runPackageCheck. See stacktrace below:\n' +
                    rethrow.getMessage())
        }

        return new CheckPackageResult(testPkgPath, issues)
    }

    /**
     * Executes the test package or project of the given ExecutionOrder via REST api.
     * @param executionOrder is an ExecutionOrder object which defines the test environment and even the test package
     * or project
     * @return ReportInfo with report information about the test execution
     * @throws ApiException on error status codes (except 409 (busy) where it will wait until success or timeout)
     * @throws TimeoutException if the execution time exceeded the timeout
     */
    ReportInfo runTest(ExecutionOrder executionOrder) throws ApiException, TimeoutException {
        de.tracetronic.cxs.generated.et.client.model.v1.ExecutionOrder executionOrderV1
        executionOrderV1 = executionOrder.toExecutionOrderV1()
        ExecutionApi apiInstance = new ExecutionApi(apiClient)
        apiInstance.createExecution(executionOrderV1)

        Closure<Boolean> checkStatus = { Execution execution ->
            execution?.status?.key in [null, ExecutionStatus.KeyEnum.WAITING, ExecutionStatus.KeyEnum.RUNNING]
        }

        Execution execution
        while (!timeoutExceeded && checkStatus(execution = apiInstance.currentExecution)) {
            sleep(1000)
        }
        if (timeoutExceeded) {
            if (apiInstance.currentExecution.order.testCasePath == executionOrderV1.testCasePath) {
                apiInstance.abortExecution()
            }
            throw new TimeoutException("Timeout exceeded during: runTest '${executionOrder.testCasePath}'")
        }
        if (execution.result == null) {
            // tests are not running
            return null
        }
        return ReportInfo.fromReportInfo(execution.result)
    }

    /**
     * Generates a report for a given report ID. The report has the format defined by the ReportGenerationOrder
     * @param reportId ID of the test execution which should be reported
     * @param order ReportGenerationOrder with the definition of the report format
     * @return GenerationResult with information about the report generation
     */
    GenerationResult generateReport(String reportId, ReportGenerationOrder order) {
        de.tracetronic.cxs.generated.et.client.model.v1.ReportGenerationOrder orderV1 = order.toReportGenerationOrderV1()
        ReportApi apiInstance = new ReportApi(apiClient)
        apiInstance.createReportGeneration(reportId, orderV1)

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
    }

    /**
     * Uploads the report to test.guide
     * @param reportId ID of the test execution which should be uploaded to test.guide
     * @param order TGUploadOrder with the definition of the test.guide instance and project
     * @return UploadResult with information about the upload
     */
    UploadResult uploadReport(String reportId, TGUploadOrder order) {
        de.tracetronic.cxs.generated.et.client.model.v1.TGUploadOrder uploadOrderV1 = order.toTGUploadOrderV1()
        ReportApi apiInstance = new ReportApi(apiClient)
        apiInstance.createUpload(reportId, uploadOrderV1)

        Closure<Boolean> checkStatus = { TGUpload upload ->
            upload?.status?.key in [null, TGUploadStatus.KeyEnum.WAITING, TGUploadStatus.KeyEnum.RUNNING]
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
    }

    /**
     * Get the IDs of all available test reports in the ecu.test instance.
     * @return List of strings with report IDs
     */
    List<String> getAllReportIds() {
        ReportApi apiInstance = new ReportApi(apiClient)
        List<de.tracetronic.cxs.generated.et.client.model.v1.ReportInfo> reports = apiInstance.getAllReports()
        return reports*.testReportId
    }
}
