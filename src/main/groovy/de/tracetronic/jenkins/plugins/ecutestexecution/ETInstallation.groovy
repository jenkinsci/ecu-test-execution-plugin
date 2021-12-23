/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.EnvVars
import hudson.Extension
import hudson.Functions
import hudson.Util
import hudson.model.EnvironmentSpecific
import hudson.model.Node
import hudson.model.TaskListener
import hudson.slaves.NodeSpecific
import hudson.tools.ToolDescriptor
import hudson.tools.ToolInstallation
import hudson.tools.ToolProperty
import hudson.util.FormValidation
import jenkins.model.Jenkins
import net.sf.json.JSONObject
import org.apache.commons.lang.StringUtils
import org.jenkinsci.Symbol
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.QueryParameter
import org.kohsuke.stapler.StaplerRequest

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import java.lang.reflect.Array

class ETInstallation extends ToolInstallation implements
        EnvironmentSpecific<ETInstallation>, NodeSpecific<ETInstallation> {

    private static final long serialVersionUID = 1L

    private static final String UNIX_EXECUTABLE = 'ecu-test'
    private static final String WINDOWS_EXECUTABLE = 'ECU-TEST.exe'

    @DataBoundConstructor
    ETInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties)
    }

    @Override
    ETInstallation forEnvironment(EnvVars env) {
        return new ETInstallation(getName(), env.expand(getHome()), getProperties().toList())
    }

    @Override
    ETInstallation forNode(@Nonnull Node node, TaskListener log) throws IOException, InterruptedException {
        return new ETInstallation(getName(), translateFor(node, log), getProperties().toList())
    }

    @CheckForNull
    File getExeFile() {
        String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars)
        return new File(home, getExeFileName())
    }

    static String getExeFileName() {
        return Functions.isWindows() ? WINDOWS_EXECUTABLE : UNIX_EXECUTABLE
    }

    @Symbol('ecuTest')
    @Extension
    static final class DescriptorImpl extends ToolDescriptor<ETInstallation> {

        DescriptorImpl() {
            super()
            load()
        }

        @CheckForNull
        ETInstallation getInstallation(final String name) {
            return installations.find { installation -> installation.name == name }
        }

        @Override
        void setInstallations(ETInstallation... installations) {
            super.setInstallations(removeEmptyInstallations(installations).toArray(new ETInstallation[0]))
            save()
        }

        @Override
        boolean configure(StaplerRequest req, JSONObject json) {
            setInstallations(req.bindJSONToList(clazz, json.get('tool')).toArray(
                    (ETInstallation[]) Array.newInstance(clazz, 0)))
            return true
        }

        private static List<ETInstallation> removeEmptyInstallations(ETInstallation... installations) {
            return installations.findAll { install -> StringUtils.isNotBlank(install.name) }
        }

        @CheckForNull
        private static File getExeFile(final File home) {
            return home ? new File(home, getExeFileName()) : null
        }

        @Override
        String getDisplayName() {
            'ECU-TEST'
        }

        @Override
        FormValidation doCheckHome(@QueryParameter File value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER)

            FormValidation returnValue = FormValidation.ok()
            if (StringUtils.isNotEmpty(value.toString())) {
                if (value.isDirectory()) {
                    final File etExe = getExeFile(value)
                    if (!etExe.exists()) {
                        returnValue = FormValidation.error("${value} is not a valid ECU-TEST home directory.")
                    }
                } else {
                    returnValue = FormValidation.error("${value} is not a valid directory.")
                }
            } else {
                returnValue = FormValidation.warning('Entry is mandatory only if it is intended to execute ECU-TEST ' +
                        'on the Jenkins master, otherwise configure each individual agent.')
            }
            return returnValue
        }
    }
}