package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.FilePath
import hudson.Launcher
import org.jenkinsci.plugins.workflow.steps.StepContext

class PathUtil {

    /**
     * This method returns absolute paths with single forward slashes. If the input path is an absolute path on
     * the remote operating system (given by context), it is left as-is. If it is a relative path, it is absolutized
     * within the current Jenkins Pipeline Home directory, remotely.
     *
     * @param inputPath the path to be examined
     * @param context the context of the step, containing various important information about the current build
     * @return absolute path with single forward slashes
     */
    static String makeAbsoluteInPipelineHome(String inputPath, StepContext context) {
        String buildDirectory = context.get(FilePath.class).getRemote()

        FilePath file = new FilePath(context.get(Launcher.class).getChannel(), inputPath)

        def absPath = file.absolutize().getRemote()
        if (absPath.replace('\\', '/').equals(file.getRemote().replace('\\', '/'))) {
            return inputPath.replace('\\', '/')
        }

        String forwardSlashInputPath = inputPath.replace('\\', '/')
        String forwardSlashBuildDirectory = buildDirectory.replace('\\', '/')

        return forwardSlashBuildDirectory + "/" + forwardSlashInputPath
    }
}
