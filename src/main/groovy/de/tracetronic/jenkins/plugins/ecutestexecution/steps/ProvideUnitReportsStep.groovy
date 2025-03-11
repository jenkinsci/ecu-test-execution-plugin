/*
* Copyright (c) 2025 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.ProvideFilesBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.zip.ZipFile

class ProvideUnitReportsStep extends AbstractDownloadReportStep {

    public static final String DEFAULT_REPORT_GLOB = "**/UNIT/junit-report.xml"
    private static final String SUPPORT_VERSION = "2024.3"
    private double unstableThreshold;
    private double failedThreshold;
    private String reportGlob;


    @DataBoundConstructor
    ProvideUnitReportsStep() {
        super()
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

    protected ArrayList<String> processReport(File reportFile, String reportDirName, String outDirPath, TaskListener listener) {
        return new ArrayList<String>()
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L

        private final transient AbstractProvideExecutionFilesStep step

        Execution(AbstractProvideExecutionFilesStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            return new Void()
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
