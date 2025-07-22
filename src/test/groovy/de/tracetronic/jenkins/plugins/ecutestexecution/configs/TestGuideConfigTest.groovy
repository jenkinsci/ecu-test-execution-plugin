package de.tracetronic.jenkins.plugins.ecutestexecution.configs
import de.tracetronic.jenkins.plugins.ecutestexecution.TGInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.util.FormValidation
import spock.lang.Specification

class TestableTestGuideConfig extends TestGuideConfig {
    @Override
    void load() {}

    @Override
    void save() {}
}

class TestGuideConfigTest extends Specification {

    def setup() {
        // Stub static methods of ValidationUtil
        GroovyMock(ValidationUtil, global: true)
    }

    def "should accept valid tgInstallations"() {
        given:
        def config = new TestableTestGuideConfig()
        def validInstall = new TGInstallation("MyTG", "http://localhost:8080", "credId")

        ValidationUtil.validateServerUrl(_) >> FormValidation.ok()
        ValidationUtil.validateCredentialsId(_, _) >> FormValidation.ok()

        when:
        config.setTgInstallations([validInstall])

        then:
        config.getTgInstallations().size() == 1
        config.getTgInstallations().first().name == "MyTG"
    }

    def "should reject installation with empty name"() {
        given:
        def config = new TestableTestGuideConfig()
        def invalidInstall = new TGInstallation("", "http://localhost:8080", "credId")

        when:
        config.setTgInstallations([invalidInstall])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Name cannot be empty")
    }

    def "should reject installation with invalid URL"() {
        given:
        def config = new TestableTestGuideConfig()
        def invalidInstall = new TGInstallation("MyTG", "invalid-url", "credId")

        ValidationUtil.validateServerUrl(_) >> FormValidation.error("Invalid URL")

        when:
        config.setTgInstallations([invalidInstall])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("invalid format of test.guide url")
    }

    def "should reject installation with invalid credentials"() {
        given:
        def config = new TestableTestGuideConfig()
        def invalidInstall = new TGInstallation("MyTG", "http://localhost:8080", "invalidCreds")

        ValidationUtil.validateServerUrl(_) >> FormValidation.ok()
        ValidationUtil.validateCredentialsId(_, _) >> FormValidation.error("Invalid credentials")

        when:
        config.setTgInstallations([invalidInstall])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid credentials")
    }

    def "should reject duplicate installation names"() {
        given:
        def config = new TestableTestGuideConfig()
        def install1 = new TGInstallation("MyTG", "http://localhost:8080", "credId")
        def install2 = new TGInstallation("MyTG", "http://another-host:8081", "credId2")

        ValidationUtil.validateServerUrl(_) >> FormValidation.ok()
        ValidationUtil.validateCredentialsId(_, _) >> FormValidation.ok()

        when:
        config.setTgInstallations([install1, install2])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("already exists")
    }
}
