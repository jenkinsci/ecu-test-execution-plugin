/*
 * Copyright (c) 2021-2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package de.tracetronic.jenkins.plugins.ecutestexecution.util

import hudson.FilePath
import hudson.Launcher
import org.jenkinsci.plugins.workflow.steps.StepContext

import java.text.ParseException
import java.text.SimpleDateFormat

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


    /**
     * This method extracts a date from the report directory name, which is expected to be in the format
     * "yyyy-MM-dd_HHmmss", and compares it with the provided reference time.
     *
     * @param reportDir The name of the report directory, expected to contain a date string in the format "yyyy-MM-dd_HHmmss".
     * @param referenceTimeMillis The reference time in milliseconds since the epoch, typically the start time of the build.
     * @return true if the extracted date is after the reference time, false otherwise.
     *         If no valid date can be extracted from the reportDir, the method returns false.
     */
    static boolean isCreationDateAfter(String reportDir, long referenceTimeMillis) {
        String df = "yyyy-MM-dd_HHmmss"
        SimpleDateFormat dateFormat = new SimpleDateFormat(df)
        String pattern = /\d{4}-\d{2}-\d{2}_\d{6}/
        def matcher = reportDir =~ pattern
        if (matcher.find()) {
            String matchedText = matcher.group(0)
            try {
                return dateFormat.parse(matchedText).time > referenceTimeMillis
            } catch (ParseException ignore ) {
                return false
            }
        }
        return false
    }
}
