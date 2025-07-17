/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.TGInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.TGUploadOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult
import de.tracetronic.jenkins.plugins.ecutestexecution.util.CredentialsUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.StepUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Item
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.FormValidation
import hudson.util.ListBoxModel
import jenkins.security.MasterToSlaveCallable
import net.sf.json.JSONObject
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.*
import org.kohsuke.stapler.*

class UploadReportsStep extends Step {

    private String testGuideUrl
    private String credentialsId
    private int projectId
    private boolean useSettingsFromServer
    private List<AdditionalSetting> additionalSettings
    private List<String> reportIds
    private boolean failOnError
    private String tgConfiguration
    private boolean configurationMode

    @DataBoundConstructor
    UploadReportsStep() {
        super()
        this.testGuideUrl = null
        this.credentialsId = null
        this.projectId = 1
        this.useSettingsFromServer = true
        this.additionalSettings = []
        this.reportIds = []
        this.failOnError = true
        this.tgConfiguration = ''
        this.configurationMode = false
    }


    UploadReportsStep(String testGuideUrl, String credentialsId) {
        this()
        this.testGuideUrl = testGuideUrl
        this.credentialsId = credentialsId
    }

    UploadReportsStep(String tgConfiguration) {
        this()
        this.tgConfiguration = tgConfiguration
        this.configurationMode = true
    }

    String getTestGuideUrl() {
        if (testGuideUrl && testGuideUrl.endsWith('/')) {
            return testGuideUrl.substring(0, testGuideUrl.length() - 1)
        }
        return testGuideUrl
    }

    @DataBoundSetter
    void setTestGuideUrl(String testGuideUrl) {
        this.testGuideUrl = testGuideUrl
    }

    String getCredentialsId() {
        return credentialsId
    }

    @DataBoundSetter
    void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId
    }

    int getProjectId() {
        return projectId
    }

    @DataBoundSetter
    void setProjectId(int projectId) {
        this.projectId = projectId
    }

    boolean getUseSettingsFromServer() {
        return useSettingsFromServer
    }

    @DataBoundSetter
    void setUseSettingsFromServer(boolean useSettingsFromServer) {
        this.useSettingsFromServer = useSettingsFromServer
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
    void setReportIds(def reportIds) {
        if (reportIds instanceof String) {
            this.reportIds = StepUtil.trimAndRemoveEmpty(reportIds.split(",").toList())
        } else if (reportIds instanceof List) {
            this.reportIds = StepUtil.trimAndRemoveEmpty(reportIds)
        } else {
            this.reportIds = []
        }
    }

    boolean getFailOnError() {
        return failOnError
    }

    @DataBoundSetter
    void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError
    }

    String getTgConfiguration() {
        return tgConfiguration
    }

    @DataBoundSetter
    void setTgConfiguration(String tgConfiguration) {
        this.tgConfiguration = tgConfiguration
    }

    void setConfigurationMode(boolean configurationMode) {
        this.configurationMode = configurationMode
    }

    String getConfigurationMode() {
        return configurationMode
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        if (StringUtils.isNotBlank(tgConfiguration)) {
            TGInstallation installation = TGInstallation.get(tgConfiguration)
            if (installation != null) {
                configurationMode = true
                testGuideUrl = installation.getTestGuideUrl()
                credentialsId = installation.getCredentialsId()
                projectId = installation.getProjectId()
                useSettingsFromServer = installation.getUseSettingsFromServer()
                additionalSettings = installation.getAdditionalSettings()
            } else {
                throw new AbortException("Selected test.guide installation '${tgConfiguration}' not found.")
            }
        }

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

    static class Execution extends SynchronousNonBlockingStepExecution<List<UploadResult>> {

        private static final long serialVersionUID = 1L

        private final transient UploadReportsStep step

        Execution(UploadReportsStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected List<UploadResult> run() throws Exception {
            List<AdditionalSetting> expSettings = expandSettings(step.additionalSettings, context.get(EnvVars.class))
            Map<String, String> expSettingsMap = toSettingsMap(expSettings)

            StandardCredentials credentials = CredentialsUtil.getCredentials(context.get(Run.class).getParent(), step.credentialsId)
            if (credentials == null) {
                throw new AbortException("No credentials found for authentication key. " +
                        "Please check the credentials configuration.")
            }
            String authKey = CredentialsUtil.getSecretString(credentials)

            try {
                return getContext().get(Launcher.class).getChannel().call(
                        new ExecutionCallable(step.testGuideUrl, authKey,
                                step.projectId, step.useSettingsFromServer,
                                expSettingsMap, step.reportIds, step.failOnError,
                                context.get(EnvVars.class),
                                context.get(TaskListener.class)))
            } catch (Exception e) {
                throw new AbortException("Upload failed: ${e.message}")
            }

        }
    }

    private static final class ExecutionCallable extends MasterToSlaveCallable<List<UploadResult>, IOException> {

        private static final long serialVersionUID = 1L

        private final String testGuideUrl
        private final String authKey
        private final int projectId
        private final boolean useSettingsFromServer
        private final Map<String, String> additionalSettings
        private List<String> reportIds
        private boolean failOnError
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(String testGuideUrl, String authKey, int projectId, boolean useSettingsFromServer,
                          Map<String, String> additionalSettings, List<String> reportIds, boolean failOnError,
                          EnvVars envVars, TaskListener listener) {
            this.testGuideUrl = envVars.expand(testGuideUrl)
            this.authKey = authKey
            this.projectId = projectId
            this.useSettingsFromServer = useSettingsFromServer
            this.additionalSettings = additionalSettings
            this.reportIds = reportIds.collect { id -> envVars.expand(id) }
            this.failOnError = failOnError
            this.envVars = envVars
            this.listener = listener
        }

        @Override
        List<UploadResult> call() throws IOException {
            List<UploadResult> result = []
            RestApiClient apiClient = RestApiClientFactory.getRestApiClient(envVars.get('ET_API_HOSTNAME'), envVars.get('ET_API_PORT'))

            TGUploadOrder uploadOrder = new TGUploadOrder(testGuideUrl, authKey, projectId, useSettingsFromServer, additionalSettings)
            listener.logger.println("Uploading reports to test.guide ${this.testGuideUrl}...")

            if (reportIds == null || reportIds.isEmpty()) {
                reportIds = apiClient.getAllReportIds()
            }

            Integer cntReports = reportIds.size()
            Integer cntStable = 0

            reportIds.each { reportId ->
                listener.logger.println("- Uploading ATX report for report id ${reportId}...")

                UploadResult uploadResult = apiClient.uploadReport(reportId, uploadOrder)
                boolean resultError = uploadResult.uploadResult.toLowerCase() == 'error'
                listener.logger.println("  -> ${uploadResult.uploadMessage}")
                if (!resultError) {
                    cntStable += 1
                } else if (resultError && failOnError) {
                    listener.logger.flush()
                    throw new AbortException("Build result set to ${Result.FAILURE.toString()} due to failed report upload. " +
                            "Set Pipeline step property 'Fail On Error' to 'false' to ignore failed report uploads.")
                }
                result.add(uploadResult)
            }

            if ( cntReports == cntStable) {
                listener.logger.println("Report upload(s) successful")
            } else {
                listener.logger.println("Report upload(s) unstable. Please see the logging of the uploads.")
            }

            return result
        }
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        @Override
        String getFunctionName() {
            'ttUploadReports'
        }

        @Override
        String getDisplayName() {
            '[TT] Upload ecu.test reports to test.guide'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class, Run.class)
        }

        @Override
        UploadReportsStep newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            def processedFormData = processFormData(formData)
            return (UploadReportsStep) super.newInstance(req, processedFormData);
        }

        protected static JSONObject processFormData(JSONObject formData) {
            if (!formData.containsKey("configurationMode")) {
                return formData
            }

            boolean configMode = formData.getBoolean("configurationMode")

            if (configMode) {
                formData.remove("testGuideUrl")
                formData.remove("credentialsId")
                formData.remove("projectId")
                formData.remove("useSettingsFromServer")
                formData.remove("additionalSettings")
            } else {
                formData.put("configurationMode", false)
                formData.put("tgConfiguration", "")
            }
            return formData
        }

        ListBoxModel doFillTgConfigurationItems() {
            ListBoxModel model = new ListBoxModel()
            TGInstallation.all().each { installation ->
                model.add(installation.getName())
            }
            return model
        }

        FormValidation doCheckTestGuideUrl(@QueryParameter String value) {
            return ValidationUtil.validateServerUrl(value)
        }

        ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            return CredentialsUtil.fillCredentialsIdItems(item, credentialsId)
        }

        FormValidation doCheckCredentialsId(@AncestorInPath Item item, @QueryParameter String value) {
            return ValidationUtil.validateCredentialsId(item, value)
        }
    }
}
