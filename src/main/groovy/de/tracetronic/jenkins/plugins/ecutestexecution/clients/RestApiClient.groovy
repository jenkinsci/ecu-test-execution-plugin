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
     * @param testPkgPath the path to the package or project to be checked
     * @return CheckPackageResult with the result of the check
     * @throws ApiException on error status codes.
     */
    abstract CheckPackageResult runPackageCheck(String testPkgPath) throws ApiException

    /**
     * Executes the test package or project of the given ExecutionOrder via REST api.
     * @param executionOrder is an ExecutionOrder object which defines the test environment and even the test package
     *   or project
     * @param timeout Time in seconds until the test execution will be aborted
     * @return ReportInfo with report information about the test execution
     */
    abstract ReportInfo runTest(ExecutionOrder executionOrder, int timeout)

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
