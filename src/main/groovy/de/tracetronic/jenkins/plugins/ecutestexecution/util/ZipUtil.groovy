/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.util

import org.apache.tools.ant.types.selectors.SelectorUtils

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
                        outputFile.parentFile.mkdirs()
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

    static String recreateZipWithFilteredFiles(File reportDirZip, List<String> includePaths, File outputZip) {
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(reportDirZip))
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(outputZip))

        try {
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && includePaths.any { path -> entry.name.endsWith(path) }) {
                    def baseFolderName = includePaths[0].split('/')[0]
                    def newEntryName = entry.name.startsWith("$baseFolderName/")
                            ? entry.name.substring(baseFolderName.length() + 1)
                            : entry.name

                    zipOutputStream.putNextEntry(new ZipEntry(newEntryName))
                    zipOutputStream << zipInputStream
                    zipOutputStream.closeEntry()
                }
            }
        } finally {
            zipInputStream.close()
            zipOutputStream.close()
        }

        return outputZip.path
    }

    static List<String> getAllMatchingPaths(zip, pattern) {
        ArrayList<String> matchingPaths = []
        new ZipInputStream(new FileInputStream(zip)).withCloseable { zipInputStream ->
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (SelectorUtils.match(pattern, entry.name)) {
                    matchingPaths.add(entry.getName())
                }
            }
        }
        return matchingPaths
    }
}
