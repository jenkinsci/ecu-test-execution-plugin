/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.cxs.generated.et.client.api.v2.ConfigurationApi
import de.tracetronic.cxs.generated.et.client.model.v2.ConfigurationStatus
import de.tracetronic.cxs.generated.et.client.model.v2.ModelConfiguration
import de.tracetronic.cxs.generated.et.client.model.v2.SimpleMessage
import de.tracetronic.jenkins.plugins.ecutestexecution.ETInstallation
import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.StopToolOptions
import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import hudson.Functions
import hudson.model.Result
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepConfigTester
import org.jvnet.hudson.test.JenkinsRule

class LoadConfigurationStepIT extends IntegrationTestBase {

    def setup() {
        ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class)
        String executablePath = Functions.isWindows() ? 'C\\ecu.test\\ECU-TEST.exe' : 'bin/ecu-test'
        etDescriptor.setInstallations(new ETInstallation('ecu.test', executablePath, JenkinsRule.NO_PROPERTIES))
    }

    // Round trip tests
    def 'Default config round trip'() {
        given:
            LoadConfigurationStep before = new LoadConfigurationStep("", "")
        when:
            LoadConfigurationStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip with tbc/tcf only'() {
        given:
            LoadConfigurationStep before = new LoadConfigurationStep('config.tbc', 'config.tcf')
        when:
            LoadConfigurationStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip with constants only'() {
        given:
            LoadConfigurationStep before = new LoadConfigurationStep("", "")
            before.setConstants([new Constant('constLabel', 'constValue')])
        when:
            LoadConfigurationStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip with startConfig false only'() {
        given:
            LoadConfigurationStep before = new LoadConfigurationStep("", "")
            before.setStartConfig(false)
        when:
            LoadConfigurationStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    def 'Config round trip with values (all fields)'() {
        given:
            LoadConfigurationStep before = new LoadConfigurationStep('config.tbc', 'config.tcf')
            before.setStartConfig(false)
            before.setConstants([new Constant('constLabel', 'constValue')])
        when:
            LoadConfigurationStep after = new StepConfigTester(jenkins).configRoundTrip(before)
        then:
            jenkins.assertEqualDataBoundBeans(before, after)
    }

    // Snippet generator tests
    def 'Snippet generator minimal'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            LoadConfigurationStep step = new LoadConfigurationStep("", "")
        then:
            st.assertRoundTrip(step, "ttLoadConfig tbcPath: '', tcfPath: ''")
    }

    def 'Snippet generator with empty TBC/TCF'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            LoadConfigurationStep step = new LoadConfigurationStep("", "")
        then:
            st.assertRoundTrip(step, "ttLoadConfig tbcPath: '', tcfPath: ''")
    }

    def 'Snippet generator with startConfig false only'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            LoadConfigurationStep step = new LoadConfigurationStep("", "")
            step.setStartConfig(false)
        then:
            st.assertRoundTrip(step, "ttLoadConfig startConfig: false, tbcPath: '', tcfPath: ''")
    }

    def 'Snippet generator with constants only'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            LoadConfigurationStep step = new LoadConfigurationStep("", "")
            step.setConstants([new Constant('constLabel', 'constValue')])
        then:
            st.assertRoundTrip(step, "ttLoadConfig constants: [[label: 'constLabel', value: 'constValue']], tbcPath: '', tcfPath: ''")
    }

    def 'Snippet generator with options only'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            LoadConfigurationStep step = new LoadConfigurationStep("", "")
            StopToolOptions options = new StopToolOptions()
            options.setStopOnError(false)
            options.setStopUndefinedTools(false)
            step.setStopOptions(options)
        then:
            st.assertRoundTrip(step, "ttLoadConfig stopOptions: [stopOnError: false, stopUndefinedTools: false], tbcPath: '', tcfPath: ''")
    }

    def 'Snippet generator with constants and startConfig false and paths'() {
        given:
            SnippetizerTester st = new SnippetizerTester(jenkins)
        when:
            LoadConfigurationStep step = new LoadConfigurationStep("", "")
            step.setTbcPath('config.tbc')
            step.setTcfPath('config.tcf')
            step.setStartConfig(false)
            step.setConstants([new Constant('constLabel', 'constValue')])
        then:
            st.assertRoundTrip(step, "ttLoadConfig constants: [[label: 'constLabel', value: 'constValue']], " +
                    "startConfig: false, tbcPath: 'config.tbc', tcfPath: 'config.tcf'")
    }

    // Pipeline smoke tests
    def 'Pipeline usage default'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
        and:
            def modelConfiguration = new ModelConfiguration()
            def configStatus = new ConfigurationStatus()
            configStatus.setKey(ConfigurationStatus.KeyEnum.FINISHED)
            modelConfiguration.setStatus(configStatus)
            GroovySpy(ConfigurationApi, global: true) {
                manageConfiguration(_) >> new SimpleMessage()
                getLastConfigurationOrder() >>  modelConfiguration
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipelineLoadConfigDefault')
            job.setDefinition(new CpsFlowDefinition("node { ttLoadConfig() }", true))

        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())

        then:
            jenkins.assertLogContains('Finished: SUCCESS', run)
    }

    def 'Pipeline usage with config data'() {
        given:
            GroovyMock(RestApiClientFactory, global: true)
            def restApiClient =  new RestApiClientV2('','')
            RestApiClientFactory.getRestApiClient(*_) >> restApiClient
        and:
            def modelConfiguration = new ModelConfiguration()
            def configStatus = new ConfigurationStatus()
            configStatus.setKey(ConfigurationStatus.KeyEnum.FINISHED)
            modelConfiguration.setStatus(configStatus)
            GroovySpy(ConfigurationApi, global: true) {
                manageConfiguration(_) >> new SimpleMessage()
                getLastConfigurationOrder() >>  modelConfiguration
            }
        and:
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, 'pipelineLoadConfigData')
            job.setDefinition(new CpsFlowDefinition("node { ttLoadConfig tbcPath: 'config.tbc', tcfPath: 'config.tcf', " +
                    "constants: [[label: 'constLabel', value: 'constValue']], startConfig: false }", true))

        when:
            WorkflowRun run = jenkins.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0).get())

        then:
            jenkins.assertLogContains('Finished: SUCCESS', run)
    }
}
