package de.tracetronic.jenkins.plugins.ecutestexecution.scan

import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import hudson.Launcher
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class TestPackageScannerIT extends IntegrationTestBase {

    @Rule
    TemporaryFolder folder = new TemporaryFolder()

    Launcher launcher
    StepContext context

    def setup() {
        launcher = jenkins.createOnlineSlave().createLauncher(jenkins.createTaskListener())
        context = Mock()
        context.get(Launcher.class) >> launcher
    }

    def 'Test Get File Extension'() {
        given:
            TestPackageScanner testPackageScanner = new TestPackageScanner(null, true, null)

        expect:
            testPackageScanner.getFileExtension() == '.pkg'
    }

    def 'Test No Packages'() {
        given:
        TestPackageScanner testPackageScanner = new TestPackageScanner(folder.newFolder().getAbsolutePath(),
                false, context)

        when:
            List<String> testFiles = testPackageScanner.scanTestFiles()

        then:
            testFiles.isEmpty()
    }

    def 'Test Scan Packages'() {
        given:
            final File testFolder = folder.newFolder()
            final File testFile = File.createTempFile("test", ".pkg", testFolder)
            final File testFile2 = File.createTempFile("test2", ".pkg", testFolder)

            TestPackageScanner testPackageScanner = new TestPackageScanner(testFolder.getAbsolutePath(), false, context)

        when:
            List<String> testFiles = testPackageScanner.scanTestFiles()

        then:
            testFiles.size() == 2
            testFiles.contains(testFile.getAbsolutePath())
            testFiles.contains(testFile2.getAbsolutePath())
    }

    def 'Test Recursive Scan'() {
        given:
            final File testPackage = folder.newFile("test.pkg")
            final File subFolder = folder.newFolder("TestSubFolder")
            final File subPackage = File.createTempFile("test", ".pkg", subFolder)
            final File subPackage2 = File.createTempFile("test2", ".pkg", subFolder)

            TestPackageScanner testPackageScanner = new TestPackageScanner(folder.getRoot().getAbsolutePath(),
                    true, context)

        when:
            List<String> testFiles = testPackageScanner.scanTestFiles()

        then:
            testFiles.size() == 3
            testFiles.contains(testPackage.getAbsolutePath())
            testFiles.contains(subPackage.getAbsolutePath())
            testFiles.contains(subPackage2.getAbsolutePath())
    }

    def 'Test File Pattern'(boolean recursive, String expectedPattern) {
        given:
            TestPackageScanner testPackageScanner = new TestPackageScanner(null, recursive, null)

        expect:
            expectedPattern == testPackageScanner.getFilePattern()

        where:
            recursive   | expectedPattern
            true        | '**/*.pkg'
            false       | '*.pkg'
    }

    def 'Test Input Dir Does Not Exists'() {
        given:
            TestPackageScanner testPackageScanner = new TestPackageScanner('/no/valid/path',
                    false, context)

        when:
            testPackageScanner.scanTestFiles()

        then:
            thrown IllegalStateException
    }
}
