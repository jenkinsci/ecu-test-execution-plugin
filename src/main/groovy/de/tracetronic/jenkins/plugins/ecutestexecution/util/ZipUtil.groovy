/*
 * Copyright (c) 2024 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtil {

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
                        String entryPath = entry.name.replace("\\", "/")
                        File outputFile = new File(saveToDirPath, entryPath)
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


    static String recreateWithPath(File zip, String target, File outputZip, boolean stripBasePath = false) {
        Path targetPath = Paths.get(target.replace("\\", "/")).normalize()
        new ZipInputStream(new FileInputStream(zip)).withCloseable { inputStream ->
            new ZipOutputStream(new FileOutputStream(outputZip)).withCloseable { outPutStream ->
                ZipEntry entry
                while ((entry = inputStream.getNextEntry()) != null) {
                    Path entryPath = Paths.get(entry.name.replace("\\", "/")).normalize()
                    if (entryPath.startsWith(targetPath)) {
                        String outputEntryName = entry.name.replace("\\", "/")
                        if (stripBasePath) {
                            outputEntryName = targetPath.relativize(entryPath).toString()
                        }
                        outPutStream.putNextEntry(new ZipEntry(outputEntryName))
                        outPutStream << inputStream
                        outPutStream.closeEntry()
                    }
                }
            }
        }
        return outputZip.path
    }


    static String recreateWithEndings(File zip, List<String> includePaths, File outputZip) {
        List<String> normalizedIncludePaths = includePaths.collect { it.replace("\\", "/") }
        new ZipInputStream(new FileInputStream(zip)).withCloseable { inputStream ->
            new ZipOutputStream(new FileOutputStream(outputZip)).withCloseable { outPutStream ->
                ZipEntry entry
                while ((entry = inputStream.getNextEntry()) != null) {
                    String normalizedEntryName = entry.name.replace("\\", "/")
                    if (!entry.isDirectory() && normalizedIncludePaths.any { path -> normalizedEntryName.endsWith(path) }) {
                        outPutStream.putNextEntry(new ZipEntry(normalizedEntryName))
                        outPutStream << inputStream
                        outPutStream.closeEntry()
                    }
                }
            }
        }
        return outputZip.path
    }

}
