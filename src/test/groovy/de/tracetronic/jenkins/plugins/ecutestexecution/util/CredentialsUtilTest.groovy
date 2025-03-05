package de.tracetronic.jenkins.plugins.ecutestexecution.util


import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.FileCredentials
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import spock.lang.Specification

class CredentialsUtilTest extends Specification {

    private StandardUsernamePasswordCredentials usernamePasswordCredentials
    private StringCredentials stringCredentials
    private StandardCredentials unknownCredentials

    def setup() {
        usernamePasswordCredentials = Mock(StandardUsernamePasswordCredentials)
        stringCredentials = Mock(StringCredentials)
        unknownCredentials = Mock(FileCredentials)
    }

    def "return empty string for unsupported credentials"() {
        when:
            def result = CredentialsUtil.getSecretString(unknownCredentials)
        then:
            result == ""
    }

    def "return plain password for string credentials"() {
        when:
            def result = CredentialsUtil.getSecretString(stringCredentials)
        then:
            1 * stringCredentials.getSecret() >> { Secret.fromString("secretString") }
            result == "secretString"
    }

    def "return plain password for username password credentials"() {
        when:
            def result = CredentialsUtil.getSecretString(usernamePasswordCredentials)
        then:
            1 * usernamePasswordCredentials.getPassword() >> { Secret.fromString("password") }
            result == "password"
    }
}
