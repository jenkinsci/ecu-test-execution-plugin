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

/**
 * This class manages the ECU-TEST tool installations, found under "Global Tool Configuration" in Jenkins.
 */
class ETInstallation extends ToolInstallation implements
        EnvironmentSpecific<ETInstallation>, NodeSpecific<ETInstallation> {

    private static final long serialVersionUID = 1L

    private static final List<String> UNIX_EXECUTABLES = ['ecu-test', 'trace-check']
    private static final List<String> WINDOWS_EXECUTABLES = ['ECU-TEST.exe', 'TRACE-CHECK.exe']

    @DataBoundConstructor
    /**
     * Constructor.
     * @param name the name of the tool installation (selectable in Jenkins jobs)
     * @param home path to the tool executable
     * @param properties properties of the tool installation
     */
    ETInstallation(String name, String home, List<? extends ToolProperty<? extends ToolInstallation>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties)
    }

    @Override
    /**
     * This method returns an environment-specific instance of ETInstallation.
     * @param env the environment within which the path to the executable is resolved
     * @return an environment-specific instance of ETInstallation
     */
    ETInstallation forEnvironment(EnvVars env) {
        return new ETInstallation(getName(), env.expand(getHome()), getProperties().toList())
    }

    @Override
    /**
     * This method returns a node-specific instance of ETInstallation.
     * @param node the node on which the path to the executable is resolved
     * @param log the log
     * @return a node-specific instance of ETInstallation
     */
    ETInstallation forNode(@Nonnull Node node, TaskListener log) throws IOException, InterruptedException {
        return new ETInstallation(getName(), translateFor(node, log), getProperties().toList())
    }

    @CheckForNull
    File getExeFile() {
        String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars)
        if (!home) {
            throw new IllegalArgumentException("Tool executable path of '${getName()}' " +
                    "is not configured for this node!")
        }

        String exeName = Functions.isWindows() ? home.tokenize("\\")[-1] :
                home.tokenize("/")[-1]
        String executables = Functions.isWindows() ? WINDOWS_EXECUTABLES : UNIX_EXECUTABLES

        if (!executables.contains(exeName)) {
            throw new IllegalArgumentException("Tool executable path of '${getName()}': " +
                    "'${home}' does not contain a TraceTronic tool!")
        }

        return new File(home)
    }

    static List<String> getExeFileNames() {
        return Functions.isWindows() ? WINDOWS_EXECUTABLES : UNIX_EXECUTABLES
    }

    /**
     * Get names of executables of all items in the ToolInstallation list for the node where the step is executed.
     * @return list of names of executables (only filename)
     */
    static ArrayList<String> getAllExecutableNames(EnvVars envVars, Node node, TaskListener log) {
        List<String> executableNames = []
        def etToolInstallations = all().get(DescriptorImpl.class)

        for (def installation : etToolInstallations.installations) {
            String exeFilePath = installation.forEnvironment(envVars).forNode(node, log).exeFile.toString()
            String exeFileName = Functions.isWindows() ? exeFilePath.tokenize("\\")[-1] :
                    exeFilePath.tokenize("/")[-1]
            if (!executableNames.contains(exeFileName)) executableNames.add(exeFileName)
        }
        return executableNames
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

        @Override
        String getDisplayName() {
            'ECU-TEST'
        }

        @Override
        FormValidation doCheckHome(@QueryParameter File value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER)

            FormValidation returnValue = FormValidation.ok()
            if (StringUtils.isEmpty(value.toString())) {
                returnValue = FormValidation.warning('Entry is mandatory only if it is intended to execute ECU-TEST ' +
                        'on the Jenkins controller, otherwise configure each individual agent.')
            }
            return returnValue
        }
    }
}
