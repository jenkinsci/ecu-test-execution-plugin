/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.views

import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunPackageAction
import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunTestAction
import hudson.Extension
import hudson.model.Run
import hudson.model.TaskListener
import hudson.model.listeners.RunListener

import javax.annotation.Nonnull

class RunPackageActionView extends RunTestActionView {

    RunPackageActionView(Run<?, ?> run) {
        super(run)
    }

    @Override
    List<RunTestAction> getTestActions() {
        return run.getActions(RunPackageAction.class)
    }

    @Extension
    static final class RunListenerImpl extends RunListener<Run<?, ?>> {

        @Override
        void onCompleted(final Run<?, ?> run, @Nonnull final TaskListener listener) {
            if (run.getAction(RunPackageAction.class) != null) {
                run.addAction(new RunPackageActionView(run))
            }
        }
    }
}
