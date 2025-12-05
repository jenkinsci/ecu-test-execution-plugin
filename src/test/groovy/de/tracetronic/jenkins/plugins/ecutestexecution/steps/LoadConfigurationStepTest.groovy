/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import spock.lang.Specification
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import hudson.util.FormValidation

class LoadConfigurationStepTest extends Specification {

    def "default values are set by DataBoundConstructor"() {
        when:
            def step = new LoadConfigurationStep("", "")
        then:
            step.getTbcPath() == ""
            step.getTcfPath() == ""
            step.getStartConfig()
            step.getConstants().isEmpty()
    }

    def "setConstants filters out entries with blank labels"() {
        given:
            def step = new LoadConfigurationStep("", "")
            def valid = new Constant("L1", "V1")
            def blank1 = new Constant("", "X")
            def blank2 = new Constant("   ", "Y")
            def nullLabel = new Constant(null, "Z")
        when:
            step.setConstants([valid, blank1, blank2, nullLabel])
        then:
            step.getConstants().size() == 1
            step.getConstants()[0].getLabel() == "L1"
            step.getConstants()[0].getValue() == "V1"
    }

    def "setConstants treats null input as empty list"() {
        when:
            def step = new LoadConfigurationStep("", "")
            step.setConstants(null)
        then:
            step.getConstants().isEmpty()
    }

    def "getConstants returns independent list instance"() {
        when:
            def step = new LoadConfigurationStep("", "")
            step.setConstants([new Constant("A", "1")])
            def returned = step.getConstants()
            returned.add(new Constant("C", "3"))
        then:
            step.getConstants().size() == 1
    }

    def "Validate file tbc extensions for LoadConfigurationStep descriptor"() {
        given:
            def descriptor = new LoadConfigurationStep.DescriptorImpl()
        expect:
            descriptor.doCheckTbcPath(path).kind == expectedKind
        where:
            path        | expectedKind
            'test.tbc'  | FormValidation.Kind.OK
            'test.prj'  | FormValidation.Kind.ERROR
            ''          | FormValidation.Kind.OK
            null        | FormValidation.Kind.OK
    }

    def "Validate file tcf extensions for LoadConfigurationStep descriptor"() {
        given:
            def descriptor = new LoadConfigurationStep.DescriptorImpl()
        expect:
            descriptor.doCheckTcfPath(path).kind == expectedKind
        where:
            path        | expectedKind
            'test.tcf'  | FormValidation.Kind.OK
            'test.prj'  | FormValidation.Kind.ERROR
            ''          | FormValidation.Kind.OK
            null        | FormValidation.Kind.OK
    }
}
