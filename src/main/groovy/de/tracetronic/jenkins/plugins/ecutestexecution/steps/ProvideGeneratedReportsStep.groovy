/*
* Copyright (c) 2024 tracetronic GmbH
*
* SPDX-License-Identifier: BSD-3-Clause
*/
package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ZipUtil
import hudson.EnvVars
import hudson.Extension
import hudson.Launcher
import hudson.model.TaskListener
import org.apache.tools.ant.types.selectors.SelectorUtils
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ProvideGeneratedReportsStep extends AbstractProvideExecutionFilesStep {
    private static final String ICON_NAME = 'testreport'
    private static final String OUT_DIR_NAME = "generated-ecu.test-reports"
    private static final String SUPPORT_VERSION = "2024.3"
    private static final String DEFAULT_INCLUDE_PATTERN = "*"
    private String includePattern

    @DataBoundConstructor
    ProvideGeneratedReportsStep() {
        super()
        this.includePattern = DEFAULT_INCLUDE_PATTERN
        iconName = ICON_NAME
        outDirName = OUT_DIR_NAME
        supportVersion = SUPPORT_VERSION
    }

    String getIncludePattern() {
        return this.includePattern
    }

    @DataBoundSetter
    void setIncludePattern(String includePattern) {
        this.includePattern = (includePattern != null) ? includePattern : DEFAULT_INCLUDE_PATTERN;
    }


    protected ArrayList<String> processReport(File reportZip, String reportDirName, String outDirPath, TaskListener listener) {
        listener.logger.println(reportZip.path)
        listener.logger.flush()
        ArrayList<String> reportPaths = []
        HashMap<String, ArrayList<String>> folderMap = [:]
        new ZipInputStream(new FileInputStream(reportZip)).withCloseable { zipInputStream ->
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (SelectorUtils.match(includePattern, entry.name) && entry.name.contains('/')) {
                    def folderName = entry.name.substring(0, entry.name.indexOf('/'))
                    if (reportDirName.contains(folderName)) {
                        // always exclude execution report
                        continue
                    }
                    def folderEntryPaths = folderMap.getOrDefault(folderName, [])
                    folderEntryPaths.add(entry.getName())
                    folderMap.put(folderName, folderEntryPaths)
                }
            }
        }
        folderMap.each { k, v ->
            def outputFile = new File("${outDirPath}/${reportDirName}/${k}.zip")
            outputFile.parentFile.mkdirs()
            reportPaths.add(ZipUtil.recreateWithFilesOfType(reportZip, v, outputFile))
        }

        if (reportPaths.size() == 0) {
            listener.logger.println("[WARNING] Could not find any matching generated report files in ${reportDirName}!")
        }
        return reportPaths
    }

    @Extension
    static final class DescriptorImpl extends StepDescriptor {
        @Override
        String getFunctionName() {
            'ttProvideGeneratedReports'
        }

        @Override
        String getDisplayName() {
            '[TT] Provide generated ecu.test reports as job artifacts.'
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, EnvVars.class, TaskListener.class)
        }
    }
}
