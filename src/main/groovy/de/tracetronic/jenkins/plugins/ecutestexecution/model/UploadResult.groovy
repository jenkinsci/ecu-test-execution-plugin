/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.model

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted

class UploadResult implements Serializable {

    private static final long serialVersionUID = 1L

    private final String uploadResult
    private final String uploadMessage

    UploadResult(String uploadResult, String uploadMessage) {
        this.uploadResult = uploadResult
        this.uploadMessage = uploadMessage
    }

    @Whitelisted
    String getUploadResult() {
        return uploadResult
    }

    @Whitelisted
    String getUploadMessage() {
        return uploadMessage
    }

    @Override
    String toString() {
        """
        -> result: ${uploadResult}
        -> message: ${uploadMessage}"
        """.stripIndent().trim()
    }
}
