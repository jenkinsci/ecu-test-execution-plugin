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


    static String recreateWithPath(File zip, String targetPath, File outputZip) {
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zip))
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(outputZip))
        Path target = Paths.get(targetPath)

        try {
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = Paths.get(entry.getName())
                if (entryPath.getNameCount() >= target.getNameCount() && entryPath.subpath(0, target.getNameCount()).equals(target)) {
                    zipOutputStream.putNextEntry(new ZipEntry(entry.name))
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


    static String recreateWithEndings(File zip, List<String> includePaths, File outputZip) {
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zip))
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(outputZip))

        try {
            ZipEntry entry
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && includePaths.any { path -> entry.name.endsWith(path) }) {
                    zipOutputStream.putNextEntry(new ZipEntry(entry.name))
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

    static String moveFromPathToBaseFolder(File zip, String pathToMove) {
        Path tempZipPath = Files.createTempFile("temp_zip", ".zip")
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zip))
        ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(tempZipPath))
        
        String normalizedPathToMove = pathToMove.replace('\\', '/')
        if (!normalizedPathToMove.endsWith('/')) {
            normalizedPathToMove += '/'
        }

        try {
            ZipEntry entry
            while ((entry = zipInputStream.nextEntry) != null) {
                String normalizedEntryName = entry.name.replace('\\', '/')
                String path

                if (normalizedEntryName.contains(normalizedPathToMove)) {
                    path = normalizedEntryName.replace(normalizedPathToMove, "")
                } else {
                    path = normalizedEntryName
                }
                zipOutputStream.putNextEntry(new ZipEntry(path))
                zipInputStream.transferTo(zipOutputStream)
                zipOutputStream.closeEntry()
            }
        } finally {
            zipInputStream.close()
            zipOutputStream.close()
        }

        Files.move(tempZipPath, zip.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return zip.path
    }
}
