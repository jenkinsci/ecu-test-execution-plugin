package de.tracetronic.jenkins.plugins.ecutestexecution.builder

import de.tracetronic.jenkins.plugins.ecutestexecution.views.ProvideFilesActionView
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import org.jenkinsci.plugins.workflow.steps.StepContext

class ProvideFilesBuilder implements Serializable {
    private static final long serialVersionUID = 1L

    final StepContext context

    ProvideFilesBuilder(StepContext context) {
        this.context = context
    }

    boolean archiveFiles(List<String> filePaths, String outDirName, boolean keepArtifacts, String iconName) throws Exception {
        Run run = context.get(Run.class)
        FilePath workspace = context.get(FilePath.class)
        TaskListener listener = context.get(TaskListener.class)
        if (!filePaths) {
            listener.logger.println('[WARNING] No files found!')
            listener.logger.flush()
            return false
        }
        def artifactsMap = new HashMap<String, String>()
        filePaths.each { path ->
            def relPath = path.substring(workspace.getRemote().length() + 1)
            artifactsMap.put(relPath, relPath)
        }
        run.artifactManager.archive(workspace, context.get(Launcher.class), listener, artifactsMap)
        run.addAction(new ProvideFilesActionView(run.externalizableId, outDirName, iconName))

        if (!keepArtifacts) {
            workspace.child(outDirName).deleteRecursive()
        }
        return true
    }
}
