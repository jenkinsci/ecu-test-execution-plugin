/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tasks.junit.TestResult
import hudson.tasks.junit.TestResultAction
import hudson.util.FormValidation
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter


class ProvideUnitReportsStep extends AbstractDownloadReportStep {

    public static final String DEFAULT_REPORT_GLOB = "**/junit-report.xml"
    private static final String SUPPORT_VERSION = "2024.3"
    private static final String OUT_DIR_NAME = "Unit Reports"
    private Double unstableThreshold;
    private Double failedThreshold;
    private String reportGlob;


    @DataBoundConstructor
    ProvideUnitReportsStep() {
        super()
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
        unstableThreshold = null
        failedThreshold = null
        reportGlob = DEFAULT_REPORT_GLOB
    }

    ProvideUnitReportsStep(Double failedThreshold, Double unstableThreshold) {
        super()
        this.failedThreshold = failedThreshold
        this.unstableThreshold = unstableThreshold
    }

    Double getUnstableThreshold() {
        return this.unstableThreshold
    }

    Double getFailedThreshold() {
        return this.failedThreshold
    }

    String getReportGlob() {
        return this.reportGlob?: DEFAULT_REPORT_GLOB
    }

    @DataBoundSetter
    void setUnstableThreshold(final Double value) {
        this.unstableThreshold =  value != null ? Math.max(0.0, Math.min(value, 100.0)) : null
    }

    @DataBoundSetter
    void setFailedThreshold(final Double value) {
        this.failedThreshold = value != null ? Math.max(0.0, Math.min(value, 100.0)) : null
    }

    @DataBoundSetter
    void setReportGlob(final String value) {
        this.reportGlob = StringUtils.defaultIfBlank(value, DEFAULT_REPORT_GLOB).trim()
    }

    boolean isUnstable(TestResult results) {
        if (results.totalCount == 0 || unstableThreshold == null) {
            return false
        }
        double failed = (results.failCount / results.totalCount) * 100.0
        return unstableThreshold < failed
    }

    boolean isFailure(TestResult results) {
        if (results.totalCount == 0 || failedThreshold == null) {
            return false
        }
        double failed = results.failCount / results.totalCount * 100.0
        return failedThreshold < failed
    }

    protected ArrayList<String> processReport(File reportFile, String reportDirName, String outDirPath, TaskListener listener) {
        return ZipUtil.extractFilesByGlobPattern(reportFile, reportGlob, "${outDirPath}/${reportDirName}")
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L

        private final transient ProvideUnitReportsStep step
        private final transient Run run
        private final transient TaskListener listener
        private final transient Launcher launcher

        Execution(ProvideUnitReportsStep step, StepContext context) {
            super(context)
            this.step = step
            run = context.get(Run.class)
            listener = context.get(TaskListener.class)
            launcher = context.get(Launcher.class)
        }

        ArrayList<String> getUnitReportFilePaths() throws IOException {
            return launcher.getChannel().call(new AbstractDownloadReportStep.DownloadReportCallable(step, step.publishConfig.timeout, context))
        }

        TestResult parseReportFiles(ArrayList<String> reportPaths) throws IOException {
            return launcher.getChannel().call(new ParseReportsExecutable(reportPaths))
        }

        void addResultsToRun(TestResult result) {
            TestResultAction action = run.getAction(TestResultAction.class);
            if (action != null) {
                action.mergeResult(result, listener);
            } else {
                action = new TestResultAction(run, result, listener);
                run.addAction(action);
            }
        }

        @Override
        protected Void run() throws Exception {
            try {
                ArrayList<String> reportPaths = getUnitReportFilePaths()
                TestResult testResult = parseReportFiles(reportPaths)
                testResult.tally() // needed, otherwise totalCount is 0 even when it contains test cases

                if (testResult.totalCount == 0) {
                    listener.logger.println("No unit test results found.")
                    if(!step.publishConfig.allowMissing) {
                        throw new Exception("Build result set to ${Result.FAILURE.toString()} due to missing test " +
                                "results. Adjust AllowMissing step property if this is not intended.")
                    }
                } else {
                    listener.logger.println("Found ${testResult.totalCount} test result(s) in total: " +
                            "#Passed: ${testResult.passCount}, #Failed: ${testResult.failCount}, " +
                            "#Skipped: ${testResult.skipCount}")
                    addResultsToRun(testResult)
                    listener.logger.println("Successfully added test results to Jenkins.")
                }

                Result newResult = Result.SUCCESS
                String reason = "unknown"
                if (step.isFailure(testResult)) {
                    newResult = Result.FAILURE
                    reason = "percentage of failed tests is higher than the configured threshold"
                } else if (step.isUnstable(testResult) || step.hasWarnings) {
                    newResult = Result.UNSTABLE
                    reason = step.hasWarnings ? "warnings" : "percentage of failed tests is higher than the configured threshold"
                }
                if (newResult != Result.SUCCESS) {
                    run.setResult(newResult)
                    listener.logger.println("Build result set to ${newResult} due to ${reason}.")
                }
            } catch (Exception e) {
                if (e instanceof UnsupportedOperationException) {
                    run.setResult(Result.UNSTABLE)
                } else {
                    run.setResult(Result.FAILURE)
                }
                listener.logger.println("Providing ${step.outDirName} failed!")
                listener.error(e.message)
            }
            listener.logger.flush()
        }
    }

    private static final class ParseReportsExecutable extends MasterToSlaveCallable<TestResult, IOException> implements Serializable {
        private static final long serialVersionUID = 1L

        private final ArrayList<String> reports

        ParseReportsExecutable(ArrayList<String> reportPaths) {
            super()
            reports = reportPaths
        }

        TestResult call() {
            TestResult testResult = new TestResult()
            reports.each { report ->
                testResult.parse(new File(report), null)
                testResult.tally()
            }
            return testResult
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        static String getDefaultReportGlob() {
            return DEFAULT_REPORT_GLOB
        }

        @Override
        String getFunctionName() {
            'ttProvideUnitReports'
        }

        @Override
        String getDisplayName() {
            '[TT] Provide generated unit reports as job test results.'
        }

        FormValidation doCheckUnstableThreshold(@QueryParameter String value) {
            return ValidationUtil.validateDoubleInRange(value, 0.0, 100.0)
        }

        FormValidation doCheckFailedThreshold(@QueryParameter String value) {
            return ValidationUtil.validateDoubleInRange(value, 0.0, 100.0)
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
