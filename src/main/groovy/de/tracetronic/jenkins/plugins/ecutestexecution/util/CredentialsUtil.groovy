package de.tracetronic.jenkins.plugins.ecutestexecution.util

import com.cloudbees.plugins.credentials.CredentialsMatchers
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import hudson.security.ACL
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import hudson.model.Job

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
}
