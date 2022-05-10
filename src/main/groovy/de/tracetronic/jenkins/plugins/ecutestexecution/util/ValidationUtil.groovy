/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.util.FormValidation
import hudson.util.IOUtils
import org.apache.commons.lang.StringUtils

import java.text.Normalizer

class ValidationUtil {

    /**
     * Character identifying parameterized values.
     */
    private static final String PARAMETER = '$'

    private ValidationUtil() {
        throw new UnsupportedOperationException('Utility class')
    }

    static FormValidation validateParameterizedValue(String value, boolean required = false) {
        FormValidation returnValue = FormValidation.ok()
        if (required) {
            returnValue = FormValidation.validateRequired(value)
        }
        if (returnValue == FormValidation.ok() && StringUtils.isNotEmpty(value) && value.contains(PARAMETER)) {
            returnValue = FormValidation.warning(
                    'Value cannot be resolved at validation-time, be sure to allocate with a valid value.')
        }
        return returnValue
    }

    static FormValidation validateAbsolutePath(String value) {
        FormValidation returnValue = FormValidation.ok()
        FormValidation paramFormValidation = ValidationUtil.validateParameterizedValue(value, true)
        if (paramFormValidation != returnValue) {
            return paramFormValidation
        }
        if (!IOUtils.isAbsolute(value)) {
            return FormValidation.error("Relative path value '${value}' are not supported at this field.")
        }

        return returnValue
    }

    static FormValidation validateTimeout(int timeout) {
        FormValidation returnValue = FormValidation.validateNonNegativeInteger(String.valueOf(timeout))
        if (returnValue == FormValidation.ok() && timeout <= 0) {
            returnValue = FormValidation.warning(
                    'Disabling the timeout will possibly cause this build step to run forever.')
        }
        return returnValue
    }

    static validateConfigFile(String configFilePath, String fileExtension) {
        FormValidation returnValue = validateParameterizedValue(configFilePath, false)
        if (returnValue == FormValidation.ok() && !StringUtils.isEmpty(configFilePath)) {
            if (!configFilePath.endsWith(fileExtension) && (configFilePath != 'KEEP')) {
                returnValue = FormValidation.error(
                        "${configFilePath} has to be empty, either of file type ${fileExtension} or \"KEEP\".")
            }
        }
        return returnValue
    }

    static FormValidation validateServerUrl(String serverUrl) {
        FormValidation returnValue = validateParameterizedValue(serverUrl, true)
        if (returnValue == FormValidation.ok()) {
            if (!isValidURL(serverUrl)) {
                returnValue = FormValidation.error("${serverUrl} is not a valid server URL.")
            }
        }
        return returnValue
    }

    /**
     * Checks if given URL is valid.
     *
     * @param url the URL
     * @return {@code true} if URL is valid, {@code false} otherwise
     */
    private static boolean isValidURL(String url) {
        try {
            new URL(url).toURI()
        } catch (MalformedURLException | URISyntaxException ignored) {
            return false
        }
        return true
    }
}
