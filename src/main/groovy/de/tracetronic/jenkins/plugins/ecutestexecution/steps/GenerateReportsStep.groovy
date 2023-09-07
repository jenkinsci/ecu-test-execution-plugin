/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.cxs.generated.et.client.model.ReportGeneration
import de.tracetronic.cxs.generated.et.client.model.ReportGenerationOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import de.tracetronic.jenkins.plugins.ecutestexecution.model.GenerationResult
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
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

    @DataBoundConstructor
    GenerateReportsStep(String generatorName) {
        super()
        this.generatorName = StringUtils.trimToEmpty(generatorName)
        this.additionalSettings = []
        this.reportIds = []
    }

    String getGeneratorName() {
        return generatorName
    }

    List<AdditionalSetting> getAdditionalSettings() {
        return additionalSettings.collect({new AdditionalSetting(it)})
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
        this.reportIds = reportIds ? removeEmptyReportIds(reportIds) : []
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    private static List<AdditionalSetting> removeEmptySettings(List<AdditionalSetting> settings) {
        return settings.findAll { setting -> StringUtils.isNotBlank(setting.name) }
    }

    private static List<String> removeEmptyReportIds(List<String> reportIds) {
        return reportIds.findAll { id -> StringUtils.isNotBlank(id) }
    }

    private static List<AdditionalSetting> expandSettings(List<AdditionalSetting> settings, EnvVars envVars) {
        return settings.collect { setting -> setting.expand(envVars) }
    }

    private static Map<String, String> toSettingsMap(List<AdditionalSetting> settings) {
        return settings.collectEntries { setting -> [setting.name, setting.value] }
    }

    static class Execution extends SynchronousNonBlockingStepExecution<GenerationResult> {

        private static final long serialVersionUID = 1L

        private final transient GenerateReportsStep step

        Execution(GenerateReportsStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected GenerationResult run() throws Exception {
            List<AdditionalSetting> expSettings = expandSettings(step.additionalSettings, context.get(EnvVars.class))
            Map<String, String> expSettingsMap = toSettingsMap(expSettings)

            return getContext().get(Launcher.class).getChannel().call(
                    new ExecutionCallable(step.generatorName, expSettingsMap, step.reportIds,
                            context.get(EnvVars.class), context.get(TaskListener.class)))
        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<GenerationResult, IOException> {
        
        private static final long serialVersionUID = 1L

        private final String generatorName
        private final Map<String, String> additionalSettings
        private List<String> reportIds
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(String generatorName, Map<String, String> additionalSettings, List<String> reportIds,
                          EnvVars envVars, TaskListener listener) {
            super()
            this.generatorName = envVars.expand(generatorName)
            this.additionalSettings = additionalSettings
            this.reportIds = reportIds.collect { id -> envVars.expand(id) }
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        GenerationResult call() throws IOException {
            GenerationResult result = null
            RestApiClient apiClient = new RestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            ReportGenerationOrder generationOrder = new ReportGenerationOrder()
                    .generatorName(generatorName)
                    .additionalSettings(additionalSettings)

            listener.logger.println("Generating ${this.generatorName} reports...")

            if (reportIds == null || reportIds.isEmpty()) {
                reportIds = apiClient.getAllReportIds()
            }
            reportIds.each { reportId ->
                listener.logger.println("- Generating ${this.generatorName} report format for report id ${reportId}...")

                ReportGeneration generation = apiClient.generateReport(reportId, generationOrder)
                result = new GenerationResult(generation.status.key.name(), generation.status.message,
                        generation.result.outputDir)

                listener.logger.println(result.toString())
            }

            listener.logger.println("${this.generatorName} reports generated successfully.")

            return result
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {
        private static final List<String> REPORT_GENERATORS = Arrays.asList('ATX', 'EXCEL', 'HTML', 'JSON', 'OMR',
                'TestSpec', 'TRF-SPLIT', 'TXT', 'UNIT')

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
            '[TT] Generate ECU-TEST reports'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
