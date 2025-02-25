/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportGenerationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import de.tracetronic.jenkins.plugins.ecutestexecution.util.StepUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.ListBoxModel
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter

class GenerateReportsStep extends Step {

    private final String generatorName
    private List<AdditionalSetting> additionalSettings
    private List<String> reportIds
    private boolean failOnError

    @DataBoundConstructor
    GenerateReportsStep(String generatorName) {
        super()
        this.generatorName = StringUtils.trimToEmpty(generatorName)
        this.additionalSettings = []
        this.reportIds = []
        this.failOnError = true
    }

    String getGeneratorName() {
        return generatorName
    }

    List<AdditionalSetting> getAdditionalSettings() {
        return additionalSettings.collect({ new AdditionalSetting(it) })
    }

    @DataBoundSetter
    void setAdditionalSettings(List<AdditionalSetting> additionalSettings) {
        this.additionalSettings = additionalSettings ? removeEmptySettings(additionalSettings) : []
    }

    List<String> getReportIds() {
        return reportIds.collect()
    }

    @DataBoundSetter
    void setReportIds(List<String> reportIds) {
        this.reportIds = reportIds ? StepUtil.removeEmptyReportIds(reportIds) : []
    }

    boolean getFailOnError() {
        return failOnError
    }

    @DataBoundSetter
    void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    private static List<AdditionalSetting> removeEmptySettings(List<AdditionalSetting> settings) {
        return settings.findAll { setting -> StringUtils.isNotBlank(setting.name) }
    }

    private static List<AdditionalSetting> expandSettings(List<AdditionalSetting> settings, EnvVars envVars) {
        return settings.collect { setting -> setting.expand(envVars) }
    }

    private static Map<String, String> toSettingsMap(List<AdditionalSetting> settings) {
        return settings.collectEntries { setting -> [setting.name, setting.value] }
    }

    static class Execution extends SynchronousNonBlockingStepExecution<List<GenerationResult>> {

        private static final long serialVersionUID = 1L

        private final transient GenerateReportsStep step

        Execution(GenerateReportsStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected List<GenerationResult> run() throws Exception {
            List<AdditionalSetting> expSettings = expandSettings(step.additionalSettings, context.get(EnvVars.class))
            Map<String, String> expSettingsMap = toSettingsMap(expSettings)

            try {
                return getContext().get(Launcher.class).getChannel().call(
                        new ExecutionCallable(step.generatorName, expSettingsMap, step.reportIds,
                                context.get(EnvVars.class), step.failOnError, context.get(TaskListener.class)))
            } catch (Exception e) {
                context.get(TaskListener.class).error(e.message)
                context.get(Run.class).setResult(Result.FAILURE)
                return [new GenerationResult("A problem occured during the report generation. See caused exception for more details.", "", null)]
            }
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<List<GenerationResult>, IOException> {

        private static final long serialVersionUID = 1L

        private final String generatorName
        private final Map<String, String> additionalSettings
        private List<String> reportIds
        private final EnvVars envVars
        private final boolean failOnError
        private final TaskListener listener

        ExecutionCallable(String generatorName, Map<String, String> additionalSettings, List<String> reportIds,
                          EnvVars envVars, boolean failOnError, TaskListener listener) {
            super()
            this.generatorName = envVars.expand(generatorName)
            this.additionalSettings = additionalSettings
            this.reportIds = reportIds.collect { id -> envVars.expand(id) }
            this.envVars = envVars
            this.failOnError = failOnError
            this.listener = listener
        }

        @Override
        List<GenerationResult> call() throws IOException {
            List<GenerationResult> result = []
            RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            ReportGenerationOrder generationOrder = new ReportGenerationOrder(generatorName, additionalSettings)
            listener.logger.println("Generating ${this.generatorName} reports...")

            if (reportIds == null || reportIds.isEmpty()) {
                reportIds = apiClient.getAllReportIds()
            }

            reportIds.each { reportId ->
                listener.logger.println("- Generating ${this.generatorName} report format for report id ${reportId}...")
                GenerationResult generationResult = apiClient.generateReport(reportId, generationOrder)

                String log = "  -> ${generationResult.generationResult}"
                if (!generationResult.generationMessage.isEmpty()) {
                    log += " (${generationResult.generationMessage})"
                }
                listener.logger.println(log)
                if (generationResult.generationResult.toLowerCase() == 'error' && failOnError) {
                    throw new AbortException("Build result set to ${Result.FAILURE.toString()} due to failed report generation. " +
                            "Set Pipeline step property 'Fail On Error' to 'false' to ignore failed report generations.)")
                }
                result.add(generationResult)
            }

            listener.logger.println("${this.generatorName} reports generated successfully.")

            return result
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {
        static final List<String> REPORT_GENERATORS = Arrays.asList('ATX', 'EXCEL', 'HTML', 'JSON',
                'TRF-SPLIT', 'TXT', 'UNIT')

        static ListBoxModel doFillGeneratorNameItems() {
            ListBoxModel model = new ListBoxModel()
            REPORT_GENERATORS.each { name ->
                model.add(name)
            }
            return model
        }

        @Override
        String getFunctionName() {
            'ttGenerateReports'
        }

        @Override
        String getDisplayName() {
            '[TT] Generate ecu.test reports'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
