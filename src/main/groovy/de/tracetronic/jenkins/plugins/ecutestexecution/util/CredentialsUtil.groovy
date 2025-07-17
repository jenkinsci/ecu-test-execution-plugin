package de.tracetronic.jenkins.plugins.ecutestexecution.util

import com.cloudbees.plugins.credentials.CredentialsMatchers
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.cloudbees.plugins.credentials.common.StandardListBoxModel
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import hudson.model.Item
import hudson.security.ACL
import hudson.util.ListBoxModel
import hudson.util.Secret
import jenkins.model.Jenkins
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import hudson.model.Job
import org.springframework.security.core.Authentication

class CredentialsUtil {

    static StandardCredentials getCredentials(Job job, String credentialsId) {
        List<StandardCredentials> credentials = CredentialsProvider.lookupCredentialsInItem(
                StandardCredentials.class, job, ACL.SYSTEM2, Collections.emptyList())
        return CredentialsMatchers.firstOrNull(credentials,
                CredentialsMatchers.allOf(
                        CredentialsMatchers.withId(credentialsId),
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                CredentialsMatchers.instanceOf(StringCredentials.class)
                        )
                ))
    }

    static String getSecretString(StandardCredentials credentials) {
        if (credentials instanceof StandardUsernamePasswordCredentials) {
            return Secret.toString(((StandardUsernamePasswordCredentials) credentials).getPassword())
        } else if (credentials instanceof StringCredentials) {
            return Secret.toString(((StringCredentials) credentials).getSecret())
        }
        return ''
    }

    static ListBoxModel fillCredentialsIdItems(Item item, String credentialsId) {
        StandardListBoxModel result = new StandardListBoxModel()
        if (!item && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return result.includeCurrentValue(credentialsId)
        }
        if (item && !item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
            return result.includeCurrentValue(credentialsId)
        }
        return result
                .includeEmptyValue()
                .includeMatchingAs((Authentication) ACL.SYSTEM2, (Item) item, StandardCredentials.class, Collections.emptyList(),
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                CredentialsMatchers.instanceOf(StringCredentials.class)
                        ))
    }
}
