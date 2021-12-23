/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution

import hudson.model.Label
import hudson.slaves.DumbSlave
import hudson.slaves.SlaveComputer
import org.apache.commons.io.IOUtils
import org.junit.Rule
import org.jvnet.hudson.test.GroovyJenkinsRule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

import java.nio.charset.StandardCharsets

import static org.junit.Assume.assumeFalse

class IntegrationTestBase extends Specification {

    @Rule
    protected GroovyJenkinsRule jenkins = new GroovyJenkinsRule()

    protected JenkinsRule.WebClient getWebClient() {
        return jenkins.createWebClient()
    }

    /**
     * Creates a dumb agent and assumes that it runs on a Windows machine.
     *
     * @return the dumb agent
     * @throws Exception signals that an exception has occurred
     */
    protected DumbSlave assumeWindowsSlave() throws Exception {
        // Windows only
        final DumbSlave agent = jenkins.createOnlineSlave(Label.get('windows'))
        final SlaveComputer computer = agent.getComputer()
        assumeFalse('Test is Windows only!', computer.isUnix())
        return agent
    }

    /**
     * Loads given class specific test resource.
     *
     * @param fileName the resource file name
     * @return the resource content
     */
    protected String loadTestResource(final String fileName) {
        try {
            return new String(IOUtils.toByteArray(
                    getClass().getResourceAsStream(getClass().getSimpleName() + '/' + fileName)), StandardCharsets.UTF_8)
        } catch (final Throwable ignored) {
            throw new RuntimeException("Could not read resource: ${fileName}.")
        }
    }
}
