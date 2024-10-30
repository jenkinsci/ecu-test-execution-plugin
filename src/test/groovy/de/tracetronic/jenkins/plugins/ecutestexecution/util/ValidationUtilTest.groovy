/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.util.FormValidation
import spock.lang.Specification

import java.nio.file.Paths

class ValidationUtilTest extends Specification {

    def "Unsupported class exception"() {
        when:
            new ValidationUtil()
        then:
            def e = thrown(UnsupportedOperationException)
            e.cause == null
            e.message == "Utility class"
    }

    def 'Validate parametrized values'(String value, boolean required, FormValidation.Kind expectedKind) {
        given:
            FormValidation validation = ValidationUtil.validateParameterizedValue(value, required)
        expect:
            validation.kind == expectedKind
        where:
            value        | required | expectedKind
            '${SETTING}' | false    | FormValidation.Kind.WARNING
            '${SETTING}' | true     | FormValidation.Kind.WARNING
            null         | false    | FormValidation.Kind.OK
            null         | true     | FormValidation.Kind.ERROR
    }

    def 'Validate absolute path values'(String value, FormValidation.Kind expectedKind) {
        given:
            FormValidation validation = ValidationUtil.validateAbsolutePath(value)
        expect:
            validation.kind == expectedKind
        where:
            value << ['..\\TestFolder',
                       Paths.get('src', 'test', 'resources', 'workspace', 'TestFolder')
                               .toFile().getAbsolutePath()]
            expectedKind << [FormValidation.Kind.ERROR, FormValidation.Kind.OK]
    }

    def 'Validate absolute path values with invalid param'() {
        expect:
            FormValidation.Kind.ERROR == ValidationUtil.validateAbsolutePath("").kind
    }

    def 'Validate timeout values'(int value, FormValidation.Kind expectedKind) {
        given:
            FormValidation validation = ValidationUtil.validateTimeout(value)
        expect:
            validation.kind == expectedKind
        where:
            value       | expectedKind
            '1'         | FormValidation.Kind.OK
            1           | FormValidation.Kind.OK
            0           | FormValidation.Kind.WARNING
            -1          | FormValidation.Kind.ERROR
    }

    def 'Validate config files'(String configFilePath, String fileExtension, FormValidation.Kind expectedKind) {
        given:
            FormValidation validation = ValidationUtil.validateConfigFile(configFilePath, fileExtension)
        expect:
            validation.kind == expectedKind
        where:
            configFilePath | fileExtension | expectedKind
            'test.tcf'     | '.tcf'        | FormValidation.Kind.OK
            'test.tcf'     | '.tbc'        | FormValidation.Kind.ERROR
            'KEEP'         | ''            | FormValidation.Kind.OK
            'KEEP'         | '.tcf'        | FormValidation.Kind.OK
            '${CONFIG}'    | ''            | FormValidation.Kind.WARNING
            ''             | ''            | FormValidation.Kind.OK
            null           | null          | FormValidation.Kind.OK
    }

    def 'Validate server URL'(String serverUrl, FormValidation.Kind expectedKind) {
        given:
            FormValidation validation = ValidationUtil.validateServerUrl(serverUrl)
        expect:
            validation.kind == expectedKind
        where:
            serverUrl                    | expectedKind
            'http://localhost:8085'      | FormValidation.Kind.OK
            'http://127.0.0.1:8085'      | FormValidation.Kind.OK
            'http://127.0.0.1:8085/path' | FormValidation.Kind.OK
            'https://127.0.0.1:8085'     | FormValidation.Kind.OK
            'invalid'                    | FormValidation.Kind.ERROR
            '127.0.0.1:8085'             | FormValidation.Kind.ERROR
            '${URL}'                     | FormValidation.Kind.WARNING
    }
}
