/*
 * Copyright (c) 2021-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import com.cloudbees.plugins.credentials.CredentialsMatchers
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import hudson.model.Item
import hudson.model.Messages
import hudson.security.ACL
import hudson.util.FormValidation
import hudson.util.IOUtils
import jenkins.model.Jenkins
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.plaincredentials.StringCredentials

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

    /**
     * Checks if the given credentials ID is valid for the specified item.
     *
     * @param item the item to check permissions against
     * @param value the credentials ID to validate
     * @return FormValidation result indicating whether the credentials ID is valid or not
     */
    static FormValidation validateCredentialsId(Item item, String value) {
        if (!item && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return FormValidation.ok()
        }

        if (item && !item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
            return FormValidation.ok()
        }

        if (StringUtils.isBlank(value)) {
            return FormValidation.ok()
        }

        if (isExpressionBasedCredentials(value)) {
            return FormValidation.warning("Cannot validate expression-based credentials")
        }

        if (!credentialsFound(item, value)) {
            return FormValidation.error("Cannot find currently selected credentials")
        }

        return FormValidation.ok()
    }

    private static boolean isExpressionBasedCredentials(String value) {
        return value.startsWith('${') && value.endsWith('}')
    }

    private static boolean credentialsFound(Item item, String value) {
        def creds = CredentialsProvider.listCredentialsInItem(
                StandardCredentials.class,
                item,
                ACL.SYSTEM2,
                Collections.emptyList(),
                CredentialsMatchers.anyOf(
                        CredentialsMatchers.withId(value),
                        CredentialsMatchers.instanceOf(StringCredentials.class)
                )
        )
        return !creds.isEmpty()
    }
}
