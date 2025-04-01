/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.model.Messages
import hudson.util.FormValidation
import hudson.util.IOUtils
import org.apache.commons.lang.StringUtils

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

    static validateFileExtension(String configFilePath, String fileExtension) {
        FormValidation returnValue = validateParameterizedValue(configFilePath, false)
        if (returnValue == FormValidation.ok() && !StringUtils.isEmpty(configFilePath)) {
            if (!configFilePath.endsWith(fileExtension)) {
                returnValue = FormValidation.error(
                        "${configFilePath} has to be of file type '${fileExtension}'")
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
     * Port of FormValidation.validateIntegerInRange for Double.
     *
     * Make sure that the given string is an double in the range specified by the lower and upper bounds (both inclusive)
     *
     * @param value the value to check
     * @param lower the lower bound (inclusive)
     * @param upper the upper bound (inclusive)
     */
    static FormValidation validateDoubleInRange(String value, double lower, double upper) {
        try {
            double doubleValue = Double.parseDouble(value);
            if (doubleValue < lower) {
                return FormValidation.error(Messages.Hudson_MustBeAtLeast(lower));
            }
            if (doubleValue > upper) {
                return FormValidation.error(Messages.Hudson_MustBeAtMost(upper));
            }
            return FormValidation.ok();
        } catch (NumberFormatException e) {
            return FormValidation.error(Messages.Hudson_NotANumber());
        }
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
