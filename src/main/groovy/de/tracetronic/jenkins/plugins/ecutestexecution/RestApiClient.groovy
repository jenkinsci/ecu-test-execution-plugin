/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import de.tracetronic.cxs.generated.et.client.ApiClient
import de.tracetronic.cxs.generated.et.client.ApiException
import de.tracetronic.cxs.generated.et.client.Configuration
import de.tracetronic.cxs.generated.et.client.api.ApiStatusApi
import de.tracetronic.cxs.generated.et.client.api.ChecksApi
import de.tracetronic.cxs.generated.et.client.api.ExecutionApi
import de.tracetronic.cxs.generated.et.client.api.ReportApi
import de.tracetronic.cxs.generated.et.client.model.CheckFinding
import de.tracetronic.cxs.generated.et.client.model.CheckReport
import de.tracetronic.cxs.generated.et.client.model.CheckExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.CheckExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.Execution
import de.tracetronic.cxs.generated.et.client.model.ExecutionOrder
import de.tracetronic.cxs.generated.et.client.model.ExecutionStatus
import de.tracetronic.cxs.generated.et.client.model.ReportGeneration
import de.tracetronic.cxs.generated.et.client.model.ReportGenerationOrder
import de.tracetronic.cxs.generated.et.client.model.ReportGenerationStatus
import de.tracetronic.cxs.generated.et.client.model.ReportInfo
import de.tracetronic.cxs.generated.et.client.model.TGUpload
import de.tracetronic.cxs.generated.et.client.model.TGUploadOrder
import de.tracetronic.cxs.generated.et.client.model.TGUploadStatus
import org.apache.commons.lang.StringUtils

class RestApiClient {

    private static final String DEFAULT_HOSTNAME = 'localhost'
    private static final String DEFAULT_PORT = '5050'

    private final ApiClient apiClient

    RestApiClient() {
        this(DEFAULT_HOSTNAME, DEFAULT_PORT)
    }

    RestApiClient(String hostName, String port) {
        apiClient = Configuration.getDefaultApiClient()
        apiClient.setBasePath(String.format('http://%s:%s/api/v1',
                StringUtils.isBlank(hostName) ? DEFAULT_HOSTNAME : StringUtils.trim(hostName),
                StringUtils.isBlank(port) ? DEFAULT_PORT : StringUtils.trim(port)))
    }

    boolean waitForAlive(int timeout = 60) {
        ApiStatusApi statusApi = new ApiStatusApi(apiClient)

        boolean alive = false
        long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
        while (System.currentTimeMillis() < endTimeMillis) {
            try {
                alive = statusApi.isAlive().message == 'Alive'
                if (alive) {
                    break
                }
            } catch (ApiException ignored) {
                sleep(1000)
            }
        }
        return alive
    }

    /**
     * This method performs the package check via the ChecksApi and returns the CheckResult.
     * Throws ApiExceptions on error status codes.
     * @param filepath the path to the package or project to be checked
     * @return the check report
     */
    CheckReport runPackageCheck(String filepath) throws ApiException {
        ChecksApi apiInstance = new ChecksApi(apiClient)
        CheckExecutionOrder order = new CheckExecutionOrder().filePath(filepath)
        String checkExecutionId = apiInstance.createCheckExecutionOrder(order).getCheckExecutionId()
        Closure<Boolean> checkStatus = { CheckExecutionStatus response ->
            response?.status in [null, 'WAITING', 'RUNNING']
        }

        while (checkStatus(apiInstance.getCheckExecutionStatus(checkExecutionId))) {
            sleep(1000)
        }
        CheckReport checkReport = apiInstance.getCheckResult(checkExecutionId)
        return checkReport
    }

    Execution runTest(ExecutionOrder executionOrder, int timeout) {
        ExecutionApi apiInstance = new ExecutionApi(apiClient)
        apiInstance.createExecution(executionOrder)

        Closure<Boolean> checkStatus = { Execution execution ->
            execution?.status?.key in [null, ExecutionStatus.KeyEnum.WAITING, ExecutionStatus.KeyEnum.RUNNING]
        }

        Execution execution
        long endTimeMillis = System.currentTimeMillis() + (long) timeout * 1000L
        while (checkStatus(execution = apiInstance.currentExecution)) {
            if (timeout > 0 && System.currentTimeMillis() > endTimeMillis) {
                apiInstance.abortExecution()
                break
            }
            sleep(1000)
        }

        return execution
    }

    ReportGeneration generateReport(String reportId, ReportGenerationOrder order) {
        ReportApi apiInstance = new ReportApi(apiClient)
        apiInstance.createReportGeneration(reportId, order)

        Closure<Boolean> checkStatus = { ReportGeneration generation ->
            generation?.status?.key in [null, ReportGenerationStatus.KeyEnum.WAITING,
                                        ReportGenerationStatus.KeyEnum.RUNNING]
        }

        ReportGeneration generation
        while (checkStatus(generation = apiInstance.getCurrentReportGeneration(reportId))) {
            sleep(1000)
        }

        return generation
    }

    TGUpload uploadReport(String reportId, TGUploadOrder order) {
        ReportApi apiInstance = new ReportApi(apiClient)
        apiInstance.createUpload(reportId, order)

        Closure<Boolean> checkStatus = { TGUpload upload ->
            upload?.status?.key in [null, TGUploadStatus.KeyEnum.WAITING, TGUploadStatus.KeyEnum.RUNNING]
        }

        TGUpload upload
        while (checkStatus(upload = apiInstance.getCurrentUpload(reportId))) {
            sleep(1000)
        }

        return upload
    }

    List<String> getAllReportIds() {
        ReportApi apiInstance = new ReportApi(apiClient)
        List<ReportInfo> reports = apiInstance.getAllReports()
        return reports*.testReportId
    }
}
