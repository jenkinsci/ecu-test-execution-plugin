package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import hudson.util.FormValidation
import spock.lang.Specification
import spock.lang.Subject

class RunProjectStepTest extends Specification {

    def "doCheckTestCasePath should return ok if valid path and .prj file extension"() {
        given:
        def descriptor = new RunProjectStep.DescriptorImpl()
        def validPath = "valid/path/to/test.prj"

        when:
        def result = descriptor.doCheckTestCasePath(validPath)

        then:
        result == FormValidation.ok()
    }
}
