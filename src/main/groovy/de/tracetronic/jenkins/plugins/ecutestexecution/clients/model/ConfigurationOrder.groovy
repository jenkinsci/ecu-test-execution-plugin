package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model


import de.tracetronic.jenkins.plugins.ecutestexecution.model.Constant
import jline.internal.Nullable

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
