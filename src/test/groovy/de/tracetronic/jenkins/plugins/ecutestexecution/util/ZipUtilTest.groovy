/*
 * Copyright (c) 2025 tracetronic GmbH
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package de.tracetronic.jenkins.plugins.ecutestexecution.util

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtilTest extends Specification {
    @TempDir
    Path tempDir

    File testZip
    File outputDir
    File outputZip

    def setup() {
        outputDir = new File(tempDir.toFile(), "extracted")
        outputZip = new File(tempDir.toFile(), "output.zip")
        testZip = new File(tempDir.toFile(), "test.zip")
        createTestZip(testZip)
    }

    private static void createTestZip(File zipFile) {
        def zip = new ZipOutputStream(new FileOutputStream(zipFile))
        try {
            zip.putNextEntry(new ZipEntry("test1.txt"))
            zip.putNextEntry(new ZipEntry("test2.xml"))
            zip.putNextEntry(new ZipEntry("folder/test3.txt"))
            zip.putNextEntry(new ZipEntry("folder\\test4.xml"))
            zip.putNextEntry(new ZipEntry("folder2/test5.json"))
            zip.putNextEntry(new ZipEntry("report/result.html"))
        } finally {
            zip.close()
        }
    }

    def "should check for presence of file types"() {
        expect:
            ZipUtil.containsFileOfType(testZip, extension) == exists

        where:
            extension | exists
            ".txt"    | true
            ".xml"    | true
            ".html"   | true
            ".pdf"    | false
    }



    def "extract files by extension"() {
        given: "a list of file extensions to extract"
            def fileEndings = [".txt"]

        when: "extracting files by extension"
            def extractedFiles = ZipUtil.extractFilesByExtension(
                testZip,
                fileEndings,
                outputDir.path
            )

        then:
            extractedFiles.size() == 2
    }

    def "should extract files by multiple extensions"() {
        given:
            def fileEndings = [".txt", ".xml"]

        when: "extracting files by extensions"
            def extractedFiles = ZipUtil.extractFilesByExtension(
                testZip,
                fileEndings,
                outputDir.path
            )

        then:
            extractedFiles.size() == 4
    }

    def "should handle empty zip file"() {
        given:
            def emptyZip = new File(tempDir.toFile(), "empty.zip")
            new ZipOutputStream(new FileOutputStream(emptyZip)).close()

        when:
            def extractedFiles = ZipUtil.extractFilesByExtension(
                emptyZip,
                [".txt"],
                outputDir.absolutePath
            )

        then:
            extractedFiles.isEmpty()
    }

    def "should extract nested directory structure"() {
        given:
            def fileEndings = [".txt", ".xml"]

        when:
            def extractedFiles = ZipUtil.extractFilesByExtension(
                testZip,
                fileEndings,
                outputDir.absolutePath
            )

        then:
            new File(outputDir, "folder").exists()
            new File(outputDir, "folder").isDirectory()
            new File(outputDir, "folder/test3.txt").exists()
            new File(outputDir, "folder/test4.xml").exists()
    }

    def "extractFilesByGlobPattern should extract by glob"() {
        when:
            def extractedFiles = ZipUtil.extractFilesByGlobPattern(
                    testZip,
                    pattern,
                    outputDir.absolutePath
            )

        then:
            extractedFiles.size() == expectedEntryCount
        where:
            pattern | expectedEntryCount
            "folder/**" | 2
            "*/*.html" | 1
            "test*" | 2
            "*.xml" | 1
            "**/*.xml" | 1
            "**.xml" | 2
    }

    def "should recreate zip with full file paths given as endings"() {
        given:
            def includePaths = ["folder/test3.txt", "folder\\test4.xml"]

        when:
            def newZipPath = ZipUtil.recreateWithEndings(testZip, includePaths, outputZip)
            def entriesInNewZip = []
            new ZipInputStream(new FileInputStream(newZipPath)).withCloseable { zipInputStream ->
                ZipEntry entry
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    entriesInNewZip.add(entry.name)
                }
            }

        then:
            entriesInNewZip.size() == 2
            entriesInNewZip.contains("folder/test3.txt")
            entriesInNewZip.contains("folder/test4.xml")
    }

    def "should recreate zip with files at path"() {
        given:
            def path = "folder"

        when:
            def newZipPath = ZipUtil.recreateWithPath(testZip, path, outputZip, false)
            def entriesInNewZip = []
            new ZipInputStream(new FileInputStream(newZipPath)).withCloseable { zipInputStream ->
                ZipEntry entry
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    entriesInNewZip.add(entry.name)
                }
            }

        then:
            entriesInNewZip.size() == 2
            entriesInNewZip.contains("folder/test3.txt")
            entriesInNewZip.contains("folder/test4.xml")
    }

    def "should recreate zip with files at striped path"() {
        given:
            def path = "folder"

        when:
            def newZipPath = ZipUtil.recreateWithPath(testZip, path, outputZip, true)
            def entriesInNewZip = []
            new ZipInputStream(new FileInputStream(newZipPath)).withCloseable { zipInputStream ->
                ZipEntry entry
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    entriesInNewZip.add(entry.name)
                }
            }

        then:
            entriesInNewZip.size() == 2
            entriesInNewZip.contains("test3.txt")
            entriesInNewZip.contains("test4.xml")
    }

    def "should skip directories when checking for file types"() {
        given:
            def zipWithDir = new File(tempDir.toFile(), "zipWithDir.zip")
            new ZipOutputStream(new FileOutputStream(zipWithDir)).withCloseable { zip ->
                zip.putNextEntry(new ZipEntry("directory/"))
                zip.putNextEntry(new ZipEntry("directory/test.txt"))
            }

        expect:
            !ZipUtil.containsFileOfType(zipWithDir, ".xml")
    }

    def "should skip directories during extraction"() {
        given:
            def zipWithDir = new File(tempDir.toFile(), "zipWithDir.zip")
            new ZipOutputStream(new FileOutputStream(zipWithDir)).withCloseable { zip ->
                zip.putNextEntry(new ZipEntry("directory/"))
            }

        when:
            def extractedFiles = ZipUtil.extractFilesByExtension(zipWithDir, [".txt"], outputDir.absolutePath)

        then:
            extractedFiles.isEmpty()
            !new File(outputDir, "directory").exists()
    }

}
