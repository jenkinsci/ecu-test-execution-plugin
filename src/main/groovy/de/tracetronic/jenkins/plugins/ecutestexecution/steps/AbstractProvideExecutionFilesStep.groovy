/*
* Copyright (c) 2024-2025 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.builder.ProvideFilesBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClient
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientFactory
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV1
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.RestApiClientV2
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ApiException
import de.tracetronic.jenkins.plugins.ecutestexecution.clients.model.ReportInfo
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.security.ControllerToAgentCallableWithTimeout
import de.tracetronic.jenkins.plugins.ecutestexecution.util.PathUtil
import de.tracetronic.jenkins.plugins.ecutestexecution.util.StepUtil
import hudson.AbortException
import hudson.EnvVars
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundSetter

import javax.annotation.Nonnull

abstract class AbstractProvideExecutionFilesStep extends AbstractDownloadReportStep {
    protected String iconName

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L

        private final transient AbstractProvideExecutionFilesStep step

        Execution(AbstractProvideExecutionFilesStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected Void run() throws Exception {
            Run run = context.get(Run.class)
            TaskListener listener = context.get(TaskListener.class)
            long startTimeMillis = run.getStartTimeInMillis()
            String outDirPath = PathUtil.makeAbsoluteInPipelineHome(step.outDirName, context)

            try {
                ArrayList<String> filePaths = context.get(Launcher.class).getChannel().call(
                        new AbstractDownloadReportStep.ExecutionCallable(step.publishConfig.timeout, startTimeMillis, context.get(EnvVars.class), outDirPath, listener, step)
                )
                def result = new ProvideFilesBuilder(context).archiveFiles(filePaths, step.outDirName, step.publishConfig.keepAll, step.iconName)
                if (!result && !step.publishConfig.allowMissing) {
                    throw new Exception("Build result set to ${Result.FAILURE.toString()} due to missing ${step.outDirName}. Adjust AllowMissing step property if this is not intended.")
                }

                result && listener.logger.println("Successfully added ${step.outDirName} to jenkins.")
                if (step.hasWarnings) {
                    run.setResult(Result.UNSTABLE)
                    listener.logger.println("Build result set to ${Result.UNSTABLE.toString()} due to warnings.")
                }
            } catch (Exception e) {
                if (e instanceof UnsupportedOperationException) {
                    run.setResult(Result.UNSTABLE)
                } else {
                    run.setResult(Result.FAILURE)
                }
                listener.logger.println("Providing ${step.outDirName} failed!")
                listener.error(e.message)
            }
            listener.logger.flush()
        }
    }


}
