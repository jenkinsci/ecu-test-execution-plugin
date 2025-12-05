/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import jline.internal.Nullable

/**
 * Abstraction of the ecu.test REST API object ConfigurationOrder in all API versions.
 */
class ConfigurationOrder implements Serializable {
    private static final long serialVersionUID = 1L

    public final boolean startConfig
    @Nullable
    public final String tbcPath
    @Nullable
    public final String tcfPath
    @Nullable
    public final List<Constant> constants

    ConfigurationOrder(String tbcPath, String tcfPath, List<Constant> constants, boolean startConfig) {
        this.tbcPath = tbcPath
        this.tcfPath = tcfPath
        this.constants = constants.collect { it -> new Constant(it) }
        this.startConfig = startConfig
    }
}
