/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.configs

import de.tracetronic.jenkins.plugins.ecutestexecution.TGInstallation
import hudson.Extension
import hudson.util.FormValidation
import jenkins.model.GlobalConfiguration
import net.sf.json.JSONObject
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.StaplerRequest
import org.apache.commons.lang3.StringUtils
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil

@Extension
class TestGuideConfig extends GlobalConfiguration  {

    private List<TGInstallation> tgInstallations = new ArrayList<TGInstallation>()
    private transient Map<String, TGInstallation> tgInstallationMap = new HashMap<>()
    private String INVALID_INSTALLATION_CONFIGURATION = "test.guide installation not saved: %s   \u24cd";

    TestGuideConfig() {
        load()
        refreshTgInstallationMap()
    }

    List<TGInstallation> getTgInstallations() {
        return tgInstallations
    }

    @DataBoundSetter
    void setTgInstallations(List<TGInstallation> newTgInstallations) {
        List<TGInstallation> tempInstallations = new ArrayList<>()
        Map<String, TGInstallation> tempInstallationMap = new HashMap<>()

        for (TGInstallation tgInstallation : newTgInstallations) {
            validateInstallation(tgInstallation)
            addInstallationToCollection(tempInstallations, tempInstallationMap, tgInstallation)
        }

        tgInstallations = tempInstallations
        tgInstallationMap = tempInstallationMap
        save()
    }

    private void validateInstallation(TGInstallation tgInstallation) {
        if (StringUtils.isBlank(tgInstallation.getName())) {
            throw new IllegalArgumentException(INVALID_INSTALLATION_CONFIGURATION.formatted("Name cannot be empty"))
        }
        if (FormValidation.Kind.OK != ValidationUtil.validateServerUrl(tgInstallation.getTestGuideUrl()).kind) {
            throw new IllegalArgumentException(INVALID_INSTALLATION_CONFIGURATION.formatted("invalid format of test.guide url"))
        }
        FormValidation credentialsValidation = ValidationUtil.validateCredentialsId(null, tgInstallation.getCredentialsId())
        if (FormValidation.Kind.ERROR == credentialsValidation.kind) {
            throw new IllegalArgumentException(INVALID_INSTALLATION_CONFIGURATION.formatted(credentialsValidation.getMessage()))
        }
    }

    private void addInstallationToCollection(
            List<TGInstallation> list, Map<String, TGInstallation> map, TGInstallation tgInstallation) {
        String name = tgInstallation.getName()
        if (map.containsKey(name)) {
            throw new IllegalArgumentException(Messages.name_exists(name))
        }
        list.add(tgInstallation)
        map.put(name, tgInstallation)
    }

    private void refreshTgInstallationMap() {
        tgInstallationMap.clear()
        for (TGInstallation tgInstallation : tgInstallations) {
            tgInstallationMap.put(tgInstallation.getName(), tgInstallation)
        }
    }

    private static List<TGInstallation> removeEmptyInstallations(TGInstallation... installations) {
        return installations.findAll { install -> StringUtils.isNotBlank(install.name) }
    }

    static TestGuideConfig get() {
        return all().get(TestGuideConfig.class)
    }

    @Override
    boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            return super.configure(req, json)
        } catch (IllegalArgumentException e) {
            throw new FormException(e.getMessage(), "tgInstallations")
        }
    }
}
