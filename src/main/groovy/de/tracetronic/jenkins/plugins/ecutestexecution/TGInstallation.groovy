package de.tracetronic.jenkins.plugins.ecutestexecution

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestGuideConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.AdditionalSetting
import de.tracetronic.jenkins.plugins.ecutestexecution.util.CredentialsUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.DescriptorExtensionList
import hudson.Extension
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import hudson.model.Item
import hudson.util.FormValidation
import hudson.util.ListBoxModel
import jenkins.model.Jenkins
import net.sf.json.JSONObject
import org.apache.commons.lang.StringUtils
import org.jenkinsci.Symbol
import org.kohsuke.stapler.AncestorInPath
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.QueryParameter
import org.kohsuke.stapler.StaplerRequest

import javax.annotation.CheckForNull
import java.lang.reflect.Array

class TGInstallation extends AbstractDescribableImpl<TGInstallation> implements Serializable {

    private static final long serialVersionUID = 1L

    private String name
    private String testGuideUrl
    private int projectId
    private String credentialsId
    private boolean useSettingsFromServer
    private List<AdditionalSetting> additionalSettings

    /**
     * Constructor.
     *
     * @param name the name of the test.guide instance (selectable in @UploadReportStep)
     * @param testGuideUrl the URL of the test.guide server
     * @param projectId the project ID to upload the report to
     * @param token the authentication token for the test.guide server
     * @param additionalSettings the list of key-value pairs to be used as test.guide upload setting
     */
    @DataBoundConstructor
    TGInstallation(String name, String testGuideUrl, String credentialsId, int projectId, boolean useSettingsFromServer,
                   List<AdditionalSetting> additionalSettings) {
        this.name = name
        this.testGuideUrl = testGuideUrl
        this.credentialsId = credentialsId
        this.projectId = projectId
        this.useSettingsFromServer = useSettingsFromServer
        this.additionalSettings = additionalSettings ?: new ArrayList<AdditionalSetting>()
    }

    TGInstallation(String name, String testGuideUrl, String credentialsId) {
        this(
                name,
                testGuideUrl,
                credentialsId,
                1,
                true,
                new ArrayList<AdditionalSetting>()
        )
    }

    // TODO: use covered constants for test or use method overloading with TgInstallation @ ttUploadReports; check if
    //  poroperties are set correctly

    String getName() {
        return name
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

    boolean getUseSettingsFromServer() {
        return useSettingsFromServer
    }

    List<AdditionalSetting> getAdditionalSettings() {
        return additionalSettings.collect({new AdditionalSetting(it)})
    }

    static TGInstallation[] all() {
        TestGuideConfig config = TestGuideConfig.get()
        if (config == null) {
            return new TGInstallation[0]
        }
        return config.getTgInstallations() as TGInstallation[]
    }

    @CheckForNull
    static TGInstallation get(final String name) {
        final TGInstallation[] installations = all()
        for (final TGInstallation installation : installations) {
            if (StringUtils.equals(name, installation.getName())) {
                return installation
            }
        }
        return null
    }

    @Symbol('test.guide')
    @Extension
    static final class DescriptorImpl extends Descriptor<TGInstallation> {

        private TGInstallation[] installations = new TGInstallation[0]

        @Override
        String getDisplayName() {
            'test.guide (ecu.test execution plugin)'
        }

        FormValidation doCheckName(@QueryParameter final String value) {
            return FormValidation.validateRequired(value)
        }

        FormValidation doCheckTestGuideUrl(@QueryParameter final String value) {
            FormValidation validation = FormValidation.validateRequired(value)
            if (validation == FormValidation.ok()) {
                return ValidationUtil.validateServerUrl(value)
            }
            return validation
        }

        FormValidation doCheckProjectId(@QueryParameter final String value) {
            FormValidation validation = FormValidation.validateRequired(value)
            if (validation != FormValidation.ok()) {
                return validation
            }

            return StringUtils.isNumeric(value) && Integer.parseInt(value) > 0
                    ? FormValidation.ok()
                    : FormValidation.error("Value must be an integer greater than 0.")
        }

        ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            return CredentialsUtil.fillCredentialsIdItems(item, credentialsId)
        }

        FormValidation doCheckCredentialsId(@AncestorInPath Item item, @QueryParameter String value) {
            return ValidationUtil.validateCredentialsId(item, value)
        }
    }

}
