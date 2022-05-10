package de.tracetronic.jenkins.plugins.ecutestexecution.scan

import org.jenkinsci.plugins.workflow.steps.StepContext

/**
 * Directory scanner searching for ECU-TEST projects.
 */
class TestProjectScanner extends AbstractTestScanner {
    /**
     * Defines the project file extension.
     */
    private static final FILE_EXTENSION = '.prj'

    TestProjectScanner(String inputDir, boolean recursive, StepContext context) {
        super(inputDir, recursive, context)
    }

    @Override
    protected String getFileExtension() {
        return FILE_EXTENSION
    }
}
