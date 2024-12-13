package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import spock.lang.Specification

class AbstractProvideExecutionFilesStepTest extends Specification {

    def "setPublishConfig assigns provided PublishConfig"() {
        given:
        def step = Spy(AbstractProvideExecutionFilesStep)
        def customConfig = new PublishConfig()
        customConfig.timeout = 120
        customConfig.allowMissing = true
        customConfig.keepAll = false

        when:
        step.setPublishConfig(customConfig)

        then:
        step.publishConfig.timeout == 120
        step.publishConfig.allowMissing
        !step.publishConfig.keepAll
    }

    def "setPublishConfig assigns default PublishConfig when null is passed"() {
        given:
        def step = Spy(AbstractProvideExecutionFilesStep)

        when:
        step.setPublishConfig(null)

        then:
        step.publishConfig != null
        step.publishConfig.timeout == PublishConfig.DEFAULT_TIMEOUT
        !step.publishConfig.allowMissing
        step.publishConfig.keepAll
    }

}
