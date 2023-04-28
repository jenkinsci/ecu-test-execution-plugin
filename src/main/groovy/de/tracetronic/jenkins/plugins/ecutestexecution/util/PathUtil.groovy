package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.FilePath
import hudson.Launcher
import org.jenkinsci.plugins.workflow.steps.StepContext

class PathUtil {

    static String makeAbsoluteInPipelineHome(String inputPath, StepContext context) {
        String buildDirectory = context.get(FilePath.class).getRemote()

        FilePath file = new FilePath(context.get(Launcher.class).getChannel(), inputPath)

        def absPath = file.absolutize().getRemote()
        if (absPath.replace('\\', '/').equals(file.getRemote().replace('\\', '/'))) {
            String independentInputPath = inputPath.replace('\\', '/')
            return independentInputPath
        }

        String independentInputPath = inputPath.replace('\\', '/')
        String independentBuildDirectory = buildDirectory.replace('\\', '/')

        return independentBuildDirectory + "/" + independentInputPath
    }
}
