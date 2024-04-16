/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.clients

import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportGenerationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.TGUploadOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult

import java.util.concurrent.TimeoutException

interface RestApiClient {

    /**
     * Waits until the ecu.test REST api is alive or timeout is reached.
     * @param timeout time in seconds to wait for alive check
     * @return boolean:
     *   true, if the the ecu.test API sends an alive signal within the timeout range
     *   false, otherwise
     */
    abstract boolean waitForAlive(int timeout)

    /**
     * This method performs the package check for the given test package or project via REST api.
     * The method will abort upon a thread interruption signal used by the TimeoutControllerToAgentCallable to handle the
     * timeout from outside this class.
     * {@see de.tracetronic.jenkins.plugins.ecutestexecution.security.TimeoutControllerToAgentCallable}
     * @param testPkgPath the path to the package or project to be checked
     * @return CheckPackageResult with the result of the check
     * @throws ApiException on error status codes (except 409 (busy) where it will wait until success or timeout)
     */
    abstract CheckPackageResult runPackageCheck(String testPkgPath) throws ApiException

    /**
     * Executes the test package or project of the given ExecutionOrder via REST api.
     * The method will abort upon a thread interruption signal used by the TimeoutControllerToAgentCallable to handle the
     * timeout from outside this class.
     * {@see de.tracetronic.jenkins.plugins.ecutestexecution.security.TimeoutControllerToAgentCallable}
     * @param executionOrder is an ExecutionOrder object which defines the test environment and even the test package
     *   or project
     * @throws ApiException on error status codes (except 409 (busy) where it will wait until success or timeout)
     * @return ReportInfo with report information about the test execution
     */
    abstract ReportInfo runTest(ExecutionOrder executionOrder) throws ApiException

    /**
     * Generates a report for a given report ID. The report has the format defined by the ReportGenerationOrder
     * @param reportId ID of the test execution which should be reported
     * @param order ReportGenerationOrder with the definition of the report format
     * @return GenerationResult with information about the report generation
     */
    abstract GenerationResult generateReport(String reportId, ReportGenerationOrder order)

    /**
     * Uploads the report to test.guide
     * @param reportId ID of the test execution which should be uploaded to test.guide
     * @param order TGUploadOrder with the definition of the test.guide instance and project
     * @return UploadResult with information about the upload
     */
    abstract UploadResult uploadReport(String reportId, TGUploadOrder order)

    /**
     * Get the IDs of all available test reports in the ecu.test instance.
     * @return List of strings with report IDs
     */
    abstract List<String> getAllReportIds()
}
