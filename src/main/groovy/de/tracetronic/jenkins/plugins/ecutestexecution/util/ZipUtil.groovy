/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.util

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtil {
    static ArrayList<String> extractFilesByExtension(File reportFolderZip, List<String> fileEndings, String saveToDirPath) {
        ArrayList<String> extractedFilePaths = []
        Set<String> fileEndingsSet = fileEndings.toSet()
        new File(saveToDirPath).mkdirs()

        new ZipInputStream(new FileInputStream(reportFolderZip)).withCloseable { zipInputStream ->
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    boolean shouldExtract = fileEndingsSet.any { ending ->
                        entry.name.endsWith(ending)
                    }

                    if (shouldExtract) {
                        File outputFile = new File(saveToDirPath, entry.name)
                        outputFile.parentFile.mkdirs() // Ensure the directory structure is created
                        outputFile.withOutputStream { outputStream ->
                            outputStream << zipInputStream
                        }
                        extractedFilePaths.add(outputFile.path)
                    }
                }
            }
        }

        return extractedFilePaths
    }

    static boolean containsFileOfType(File reportFolderZip, String fileEnding) {
        boolean result = false
        new ZipInputStream(new FileInputStream(reportFolderZip)).withCloseable { zipInputStream ->
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.name.endsWith(fileEnding)) {
                    result = true
                    break
                }
            }
        }
        return result
    }

    static String recreateWithFilesOfType(File reportDirZip, List<String> includeEndings, File outputZip) {
        new ZipInputStream(new FileInputStream(reportDirZip)).withCloseable { zipInputStream ->
            new ZipOutputStream(new FileOutputStream(outputZip)).withCloseable { zipOutputStream ->
                ZipEntry entry
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (!entry.isDirectory() && includeEndings.any { entry.name.endsWith(it) }) {
                        zipOutputStream.putNextEntry(new ZipEntry(entry.name))
                        zipOutputStream << zipInputStream
                        zipOutputStream.closeEntry()
                    }
                }
            }
        }
        return outputZip.getPath()
    }
}
