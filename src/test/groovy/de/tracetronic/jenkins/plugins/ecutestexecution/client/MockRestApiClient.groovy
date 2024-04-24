/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.client

import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportGenerationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.TGUploadOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult

import java.util.concurrent.TimeoutException

class MockRestApiClient implements RestApiClient {

    @Override
    void setTimeoutExceeded() {}

    @Override
    boolean waitForAlive(int timeout) {
        return true
    }

    @Override
    CheckPackageResult runPackageCheck(String testPkgPath) throws ApiException, TimeoutException {
        return null
    }

    @Override
    ReportInfo runTest(ExecutionOrder executionOrder) {
        return null
    }

    @Override
    GenerationResult generateReport(String reportId, ReportGenerationOrder order) {
        return null
    }

    @Override
    UploadResult uploadReport(String reportId, TGUploadOrder order) {
        return null
    }

    @Override
    List<String> getAllReportIds() {
        return null
    }
}
