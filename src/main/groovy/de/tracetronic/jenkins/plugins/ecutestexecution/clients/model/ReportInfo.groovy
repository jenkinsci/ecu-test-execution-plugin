/*
 * Copyright (c) 2024-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

/**
 * Abstraction of the ecu.test REST api object ReportInfo in all api versions.
 */
class ReportInfo implements Serializable {
    private static final long serialVersionUID = 1L

    public final String testReportId
    public final String reportDir
    public final String result
    public final List<String> subReportIds

    ReportInfo(String testReportId, String reportDir, String result, List<String> subReportIds) {
        this.testReportId = testReportId
        this.reportDir = reportDir
        this.result = result
        this.subReportIds = subReportIds
    }


    /**
     * Convert the ecu.test V2 REST api object ReportInfo to an abstract LabeledValue object.
     * @return ReportInfo abstract object with data from the V2 ReportInfo data
     */
    static ReportInfo fromReportInfo(de.tracetronic.cxs.generated.et.client.model.v2.ReportInfo reportInfo) {
        return new ReportInfo(reportInfo.testReportId, reportInfo.reportDir, reportInfo.result, reportInfo.subReportIds)
    }

}
