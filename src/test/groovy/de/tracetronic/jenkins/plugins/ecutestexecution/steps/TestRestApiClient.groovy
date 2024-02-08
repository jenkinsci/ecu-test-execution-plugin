/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ExecutionOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportGenerationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.TGUploadOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.CheckPackageResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult

class TestRestApiClient implements RestApiClient {

    @Override
    boolean waitForAlive(int timeout) {
        return true
    }

    @Override
    CheckPackageResult runPackageCheck(String testPkgPath) throws ApiException {
        return null
    }

    @Override
    ReportInfo runTest(ExecutionOrder executionOrder, int timeout) {
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
