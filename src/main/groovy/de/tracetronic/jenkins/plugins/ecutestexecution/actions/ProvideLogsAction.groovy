package de.tracetronic.jenkins.plugins.ecutestexecution.actions

import hudson.FilePath
import hudson.model.Run
import jenkins.model.RunAction2

class ProvideLogsAction implements RunAction2 {
    private transient Run<?, ?> run

    ProvideLogsAction(Run<?, ?> run) {
        this.run = run
    }

    @Override
    String getIconFileName() {
        return "clipboard.png"
    }

    @Override
    String getDisplayName() {
        return "ecu.test logs"
    }

    @Override
    String getUrlName() {
        return "et-logs"
    }

    @Override
    void onAttached(Run<?, ?> run) {
        this.run = run

    }

    @Override
    void onLoad(Run<?, ?> run) {
        this.run = run
    }

    Run<?, ?> getRun() {
        return run
    }

    List<Run.Artifact> getLogs() {
        return run?.artifacts ?: []
    }
}
