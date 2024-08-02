package de.tracetronic.jenkins.plugins.ecutestexecution.util

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipUtil {
    /**
     * Extracts and saves files containing the given strings in their name out of a given zip folder to given path.
     * @return List of extracted file paths
     */
    static ArrayList<String> extractAndSaveFromZip(File reportFolderZip, List<String> fileNamesToExtract, String saveToDirPath) {
        ArrayList<String> extractedFilePaths = []
        Set<String> fileNamesToExtractSet = fileNamesToExtract.toSet()
        new File(saveToDirPath).mkdirs()

        ZipInputStream zipInputStream = null
        try {
            zipInputStream = new ZipInputStream(new FileInputStream(reportFolderZip))
            ZipEntry entry
            while ((entry = zipInputStream.nextEntry) != null) {
                def zipFileName = entry.name.split("/")[-1]
                if (fileNamesToExtractSet.contains(zipFileName)) {
                    File outputFile = new File(saveToDirPath, zipFileName)
                    outputFile.parentFile.mkdirs()
                    FileOutputStream outputStream = null
                    try {
                        outputStream = new FileOutputStream(outputFile)
                        outputStream << zipInputStream
                    } finally {
                        if (outputStream != null) {
                            outputStream.close()
                        }
                    }
                    extractedFilePaths.add(outputFile.getPath())
                    fileNamesToExtractSet.remove(zipFileName)
                }
            }
        } finally {
            if (zipInputStream != null) {
                zipInputStream.close()
            }
        }

        return extractedFilePaths
    }
}
