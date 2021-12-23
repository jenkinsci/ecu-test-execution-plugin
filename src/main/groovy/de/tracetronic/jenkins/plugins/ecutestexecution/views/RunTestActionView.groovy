/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.views

import de.tracetronic.jenkins.plugins.ecutestexecution.actions.RunTestAction
import hudson.model.InvisibleAction
import hudson.model.Run

abstract class RunTestActionView extends InvisibleAction {

    protected final Run<?, ?> run

    protected RunTestActionView(Run<?, ?> run) {
        this.run = run
    }

    /**
     * Gets the list of {@link RunTestAction}s according to this build action.
     *
     * @return the test actions
     */
    abstract List<RunTestAction> getTestActions()
}
