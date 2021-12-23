/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.model

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

class GenerationResult implements Serializable {

    private static final long serialVersionUID = 1L

    private final String generationResult
    private final String generationMessage
    private final String reportOutputDir

    GenerationResult(String generationResult, String generationMessage, String reportOutputDir) {
        this.generationResult = generationResult
        this.generationMessage = generationMessage
        this.reportOutputDir = reportOutputDir
    }

    @Whitelisted
    String getGenerationResult() {
        return generationResult
    }

    @Whitelisted
    String getGenerationMessage() {
        return generationMessage
    }

    @Whitelisted
    String getReportOutputDir() {
        return reportOutputDir
    }

    @Override
    String toString() {
        """
        -> result: ${generationResult}
        -> message: ${generationMessage}
        -> reportOutputDir: ${reportOutputDir}
        """.stripIndent().trim()
    }
}
