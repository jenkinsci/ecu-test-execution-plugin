/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.cloudbees.plugins.credentials.CredentialsMatchers
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.cloudbees.plugins.credentials.common.StandardListBoxModel
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.TGUploadOrder
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import de.tracetronic.jenkins.plugins.ecutestexecution.model.UploadResult
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.Item
import hudson.model.Job
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.security.ACL
import hudson.util.FormValidation
import hudson.util.ListBoxModel
import hudson.util.Secret
import jenkins.model.Jenkins
import jenkins.security.MasterToSlaveCallable
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.AncestorInPath
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import javax.annotation.CheckForNull

class UploadReportsStep extends Step {

    private final String testGuideUrl
    private final String credentialsId // for authentication key
    private int projectId
    private boolean useSettingsFromServer
    private List<AdditionalSetting> additionalSettings
    private List<String> reportIds

    @DataBoundConstructor
    UploadReportsStep(String testGuideUrl, String credentialsId) {
        super()
        this.testGuideUrl = StringUtils.trimToEmpty(testGuideUrl)
        this.credentialsId = credentialsId
        this.projectId = 1
        this.useSettingsFromServer = true
        this.additionalSettings = []
        this.reportIds = []
    }

    String getTestGuideUrl() {
        if (testGuideUrl.endsWith('/')) {
            return testGuideUrl.substring(0, testGuideUrl.length() - 1)
        }
        return testGuideUrl
    }

    String getCredentialsId() {
        return credentialsId
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

            StandardUsernamePasswordCredentials credentials = getCredentials(context.get(Run.class).getParent())
            String authKey = Secret.toString(credentials.getPassword())

            try {
                return getContext().get(Launcher.class).getChannel().call(
                        new ExecutionCallable(step.testGuideUrl, authKey,
                                step.projectId, step.useSettingsFromServer,
                                expSettingsMap, step.reportIds,
                                context.get(EnvVars.class),
                                context.get(TaskListener.class)))
            } catch (Exception e) {
                context.get(TaskListener.class).error(e.message)
                context.get(Run.class).setResult(Result.FAILURE)
                return [ new UploadResult("Report upload failed",
                        "A problem occured during the report upload. See caused exception for more details.",
                        null) ]
            }

        }

        @CheckForNull
        private StandardUsernamePasswordCredentials getCredentials(Job job) {
            List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class, job, ACL.SYSTEM, Collections.emptyList())
            return CredentialsMatchers.firstOrNull(credentials, CredentialsMatchers.withId(step.credentialsId))
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
        private final EnvVars envVars
        private final TaskListener listener

        ExecutionCallable(String testGuideUrl, String authKey, int projectId, boolean useSettingsFromServer,
                          Map<String, String> additionalSettings, List<String> reportIds,
                          EnvVars envVars, TaskListener listener) {
            this.testGuideUrl = envVars.expand(testGuideUrl)
            this.authKey = authKey
            this.projectId = projectId
            this.useSettingsFromServer = useSettingsFromServer
            this.additionalSettings = additionalSettings
            this.reportIds = reportIds.collect { id -> envVars.expand(id) }
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
                if (uploadResult.reportLink != null && !uploadResult.reportLink.isEmpty()) {
                    cntStable += 1
                }
                listener.logger.println("  -> ${uploadResult.uploadMessage}")

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

        FormValidation doCheckTestGuideUrl(@QueryParameter String value) {
            return ValidationUtil.validateServerUrl(value)
        }

        ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel()
            if (isItemNullAndUserLacksAdminPermission(item)){
                return result.includeCurrentValue(credentialsId)
            }
            if (isItemNotNullAndUserLacksRequiredPermissions(item)) {
                return result.includeCurrentValue(credentialsId)
            }
            return result
                    .includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM, (Item) item, StandardCredentials.class, Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class))
        }

        FormValidation doCheckCredentialsId(@AncestorInPath Item item, @QueryParameter String value) {
            if (isItemNullAndUserLacksAdminPermission(item)) {
                return FormValidation.ok();
            }

            if (isItemNotNullAndUserLacksRequiredPermissions(item)) {
                return FormValidation.ok();
            }

            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }

            if (isExpressionBasedCredentials(value)) {
                return FormValidation.warning("Cannot validate expression-based credentials");
            }

            if (areCredentialsNotFound(item, value)) {
                return FormValidation.error("Cannot find currently selected credentials");
            }

            return FormValidation.ok();
        }

        private boolean isItemNullAndUserLacksAdminPermission(Item item) {
            return item == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER);
        }

        private boolean isItemNotNullAndUserLacksRequiredPermissions(Item item) {
            return item != null && !item.hasPermission(Item.EXTENDED_READ) &&
                    !item.hasPermission(CredentialsProvider.USE_ITEM);
        }

        private boolean isExpressionBasedCredentials(String value) {
            return value.startsWith('${') && value.endsWith('}');
        }

        private boolean areCredentialsNotFound(Item item, String value) {
            return CredentialsProvider.listCredentials(
                    StandardCredentials.class,
                    item,
                    ACL.SYSTEM,
                    Collections.emptyList(),
                    CredentialsMatchers.withId(value)
            ).isEmpty();
        }
    }
}
