/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.EnvVars
import hudson.Extension
import hudson.Functions
import hudson.Util
import hudson.model.Computer
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
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.QueryParameter
import org.kohsuke.stapler.StaplerRequest

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import java.lang.reflect.Array

/**
 * This class manages the ecu.test tool installations, found under "Global Tool Configuration" in Jenkins.
 */
class ETInstallation extends ToolInstallation implements
        EnvironmentSpecific<ETInstallation>, NodeSpecific<ETInstallation> {

    private static final long serialVersionUID = 1L

    private static final List<String> UNIX_EXECUTABLES = ['ecu-test', 'trace-check', 'ecu.test', 'trace.check']
    private static final List<String> WINDOWS_EXECUTABLES = ['ECU-TEST.exe', 'TRACE-CHECK.exe', 'ecu.test.exe',
                                                             'trace.check.exe']

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
    File getExeFileOnNode() {
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
                    "'${home}' does not contain a tracetronic tool!")
        }

        return new File(home)
    }

    static List<String> getExeFileNames() {
        return Functions.isWindows() ? WINDOWS_EXECUTABLES : UNIX_EXECUTABLES
    }

    static ETInstallation getToolInstallationForMaster(StepContext context, String toolName) {
        String expToolName = context.get(EnvVars.class).expand(toolName)
        ETInstallation installation = all().get(DescriptorImpl.class).getInstallation(expToolName)

        if (installation) {
            Computer computer = context.get(Computer)
            final Node node = computer?.getNode()
            if (node) {
                installation = installation.forNode(node, context.get(TaskListener.class))
                installation = installation.forEnvironment(context.get(EnvVars.class))
            }
        } else {
            throw new IllegalArgumentException("Tool installation ${expToolName} is not configured for this node!")
        }

        return installation
    }


    static ArrayList<ETInstallation> getAllETInstallationsOnNode(EnvVars envVars, Node node, TaskListener log) {
        List<ETInstallation> installations = []

        def etToolInstallations = all().get(DescriptorImpl.class)
        for (def installation : etToolInstallations.installations) {
            installations.add(installation.forEnvironment(envVars).forNode(node, log))
        }

        return installations
    }

    @Symbol('ecu.test')
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
            'ecu.test'
        }

        @Override
        FormValidation doCheckHome(@QueryParameter File value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER)

            FormValidation returnValue = FormValidation.ok()
            if (StringUtils.isEmpty(value.toString())) {
                returnValue = FormValidation.warning('Entry is mandatory only if it is intended to execute ecu.test ' +
                        'on the Jenkins controller, otherwise configure each individual agent.')
            }
            return returnValue
        }
    }
}
