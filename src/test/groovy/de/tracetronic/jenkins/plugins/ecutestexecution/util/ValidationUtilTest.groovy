/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import com.cloudbees.plugins.credentials.CredentialsProvider
import hudson.model.Item
import hudson.util.FormValidation
import org.eclipse.jetty.util.security.CredentialProvider
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
            FormValidation validation = ValidationUtil.validateFileExtension(configFilePath, fileExtension)
        expect:
            validation.kind == expectedKind
        where:
            configFilePath | fileExtension | expectedKind
            'test.tcf'     | '.tcf'        | FormValidation.Kind.OK
            'test.tcf'     | '.tbc'        | FormValidation.Kind.ERROR
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

    def 'Validate double value is in range'() {
        given:
            FormValidation validation = ValidationUtil.validateDoubleInRange(value, 0.0, 100.0)
        expect:
            validation.kind == expectedKind
        where:
            value       | expectedKind
            "-10.9"     | FormValidation.Kind.ERROR
            "NaN"       | FormValidation.Kind.ERROR
            "-0.001"    | FormValidation.Kind.ERROR
            "0.0"       | FormValidation.Kind.OK
            "0.001"     | FormValidation.Kind.OK
            "42"        | FormValidation.Kind.OK
            "100.0"     | FormValidation.Kind.OK
            "100.00001" | FormValidation.Kind.ERROR
            "123"       | FormValidation.Kind.ERROR
    }

    def "Validate credentials ID with different formats"() {
        given:
            def mockItem = Mock(Item)
            mockItem.hasPermission(Item.EXTENDED_READ) >> true
            mockItem.hasPermission(CredentialsProvider.USE_ITEM) >> true
        when:
            def validation = ValidationUtil.validateCredentialsId(mockItem, inputValue)
        then:
            validation.kind == expectedKind
            validation.message == expectedMessage
        where:
            inputValue            | expectedKind                  | expectedMessage
            '${expression}'       | FormValidation.Kind.WARNING   | "Cannot validate expression-based credentials"
            'validCredentialId'   | FormValidation.Kind.ERROR     | "Cannot find currently selected credentials"
            '${expression'        | FormValidation.Kind.ERROR     | "Cannot find currently selected credentials"
            'expression}'         | FormValidation.Kind.ERROR     | "Cannot find currently selected credentials"
            null                  | FormValidation.Kind.OK        | null
            ''                    | FormValidation.Kind.OK        | null
    }
}
