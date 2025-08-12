/*
 * Copyright (c) 2024-2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.builder.ProvideFilesBuilder
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution

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

            try {
                ArrayList<String> filePaths = context.get(Launcher.class).getChannel().call(
                        new AbstractDownloadReportStep.DownloadReportCallable(step, step.publishConfig.timeout, context)
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
