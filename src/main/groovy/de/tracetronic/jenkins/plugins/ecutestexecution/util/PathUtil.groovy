package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.FilePath
import org.jenkinsci.plugins.workflow.steps.StepContext

class PathUtil {

    public static String makeAbsoluteInPipelineHome(String inputPath, StepContext context) {
        String independentInputPath = inputPath.replace('\\', '/')
        String jenkinsHome = context.get(FilePath.class).getRemote().replace('\\', '/')

        File file = new File(independentInputPath)
        if (file.isAbsolute()) {
            return independentInputPath
        }

        return jenkinsHome + "/" + independentInputPath
    }
}
