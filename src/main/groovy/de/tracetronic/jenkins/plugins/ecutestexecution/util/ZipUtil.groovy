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

    static String recreateZipWithFilteredFilesFromSubfolder(File reportDirZip, String subfolderToExtractFrom, List<String> includeEndings, File outputZip) {
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(reportDirZip))
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(outputZip))

        try {
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && includeEndings.any { ending -> entry.name.toLowerCase().endsWith(ending.toLowerCase()) }) {
                    def pathComponents = entry.name.split('/') as List

                    def newPath
                    if (!subfolderToExtractFrom || subfolderToExtractFrom == "/") {
                        newPath = entry.name
                    }

                    def subfolderIndex = pathComponents.indexOf(subfolderToExtractFrom)
                    if (subfolderIndex != -1 && subfolderIndex < pathComponents.size() - 1) {
                        newPath = pathComponents[(subfolderIndex + 1)..-1].join('/')
                    }

                    if (newPath) {
                        ZipEntry newEntry = new ZipEntry(newPath)
                        zipOutputStream.putNextEntry(newEntry)
                        zipOutputStream << zipInputStream
                        zipOutputStream.closeEntry()
                    }
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
