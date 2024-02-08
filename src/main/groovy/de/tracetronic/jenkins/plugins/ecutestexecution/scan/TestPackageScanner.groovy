/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.scan

import org.jenkinsci.plugins.workflow.steps.StepContext

/**
 * Directory scanner searching for ecu.test packages.
 */
class TestPackageScanner extends AbstractTestScanner {
    /**
     * Defines the package file extension.
     */
    private static final FILE_EXTENSION = '.pkg'

    TestPackageScanner(String inputDir, boolean recursive, StepContext context) {
        super(inputDir, recursive, context)
    }

    @Override
    protected String getFileExtension() {
        return FILE_EXTENSION
    }
}
