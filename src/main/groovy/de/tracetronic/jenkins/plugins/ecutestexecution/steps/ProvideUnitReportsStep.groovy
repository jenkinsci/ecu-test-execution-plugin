/*
* Copyright (c) 2025 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.ProvideFilesBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.remoting.VirtualChannel
import hudson.tasks.junit.TestResult
import hudson.tasks.junit.TestResultAction
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter


class ProvideUnitReportsStep extends AbstractDownloadReportStep {

    public static final String DEFAULT_REPORT_GLOB = "**UNIT/junit-report.xml"
    private static final String SUPPORT_VERSION = "2024.3"
    private static final String OUT_DIR_NAME = "Unit Reports"
    private double unstableThreshold;
    private double failedThreshold;
    private String reportGlob;


    @DataBoundConstructor
    ProvideUnitReportsStep() {
        super()
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
        unstableThreshold = 0.0
        failedThreshold = 0.0
        reportGlob = DEFAULT_REPORT_GLOB
    }

    double getUnstableThreshold() {
        return this.unstableThreshold
    }

    double getFailedThreshold() {
        return this.failedThreshold
    }

    String getReportGlob() {
        return this.reportGlob?: DEFAULT_REPORT_GLOB
    }

    @DataBoundSetter
    void setUnstableThreshold(final double value) {
        this.unstableThreshold = Math.max(0.0, Math.min(value, 100.0))
    }

    @DataBoundSetter
    void setFailedThreshold(final double value) {
        this.failedThreshold = Math.max(0.0, Math.min(value, 100.0))
    }

    @DataBoundSetter
    void setReportGlob(final String value) {
        this.reportGlob = StringUtils.defaultIfBlank(value, DEFAULT_REPORT_GLOB).trim()
    }

    protected ArrayList<String> processReport(File reportFile, String reportDirName, String outDirPath, TaskListener listener) { ArrayList<String> logFileNames = ["ecu.test_out.log", "ecu.test_err.log"]
        return ZipUtil.extractFilesByGlobPattern(reportFile, DEFAULT_REPORT_GLOB, "${outDirPath}/${reportDirName}")
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L

        private final transient ProvideUnitReportsStep step

        Execution(ProvideUnitReportsStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            Run run = context.get(Run.class)
            TaskListener listener = context.get(TaskListener.class)
            VirtualChannel channel = context.get(Launcher.class).getChannel()

            long startTimeMillis = run.getStartTimeInMillis()
            String outDirPath = PathUtil.makeAbsoluteInPipelineHome(step.outDirName, context)

            try {
                ArrayList<String> filePaths = channel.call(
                        new AbstractDownloadReportStep.ExecutionCallable(step.publishConfig.timeout, startTimeMillis,
                                context.get(EnvVars.class),outDirPath, listener, step)
                )

                if (filePaths.empty && !step.publishConfig.allowMissing) {
                    throw new Exception("Build result set to ${Result.FAILURE.toString()} due to missing ${step.outDirName}. Adjust AllowMissing step property if this is not intended.")
                }

                TestResult testResult = channel.call(new ParseReportsExecutable(filePaths))
                TestResultAction action = run.getAction(TestResultAction.class);
                if (action) {
                    action.mergeResult(testResult, listener);
                } else {
                    action = new TestResultAction(run, testResult, listener);
                    run.addAction(action);
                }
                testResult.freeze(action);

                listener.logger.println("Successfully added ${step.outDirName} to Jenkins.")
                if (step.hasWarnings) {
                    run.setResult(Result.UNSTABLE)
                    listener.logger.println("Build result set to ${Result.UNSTABLE.toString()} due to warnings.")
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

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
