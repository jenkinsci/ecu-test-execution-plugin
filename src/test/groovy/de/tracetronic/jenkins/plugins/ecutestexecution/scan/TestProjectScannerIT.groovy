package de.tracetronic.jenkins.plugins.ecutestexecution.scan

import de.tracetronic.jenkins.plugins.ecutestexecution.IntegrationTestBase
import hudson.Launcher
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class TestProjectScannerIT extends IntegrationTestBase {

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
            TestProjectScanner testProjectScanner = new TestProjectScanner(null, true, null)

        expect:
            testProjectScanner.getFileExtension() == '.prj'
    }

    def 'Test No Projects'() {
        given:
            TestProjectScanner testProjectScanner = new TestProjectScanner(folder.newFolder().getAbsolutePath(),
                    false, context)

        when:
            List<String> testFiles = testProjectScanner.scanTestFiles()

        then:
            testFiles.isEmpty()
    }

    def 'Test Scan Projects'() {
        given:
            final File testFolder = folder.newFolder()
            final File testFile = File.createTempFile("test", ".prj", testFolder)
            final File testFile2 = File.createTempFile("test2", ".prj", testFolder)

            TestProjectScanner testProjectScanner = new TestProjectScanner(testFolder.getAbsolutePath(),
                        false, context)

        when:
            List<String> testFiles = testProjectScanner.scanTestFiles()

        then:
            testFiles.size() == 2
            testFiles.contains(testFile.getAbsolutePath())
            testFiles.contains(testFile2.getAbsolutePath())
    }

    def 'Test Recursive Scan'() {
        given:
            final File testProject = folder.newFile("test.prj")
            final File subFolder = folder.newFolder("TestSubFolder")
            final File subProject = File.createTempFile("test", ".prj", subFolder)
            final File subProject2 = File.createTempFile("test2", ".prj", subFolder)

            TestProjectScanner testProjectScanner = new TestProjectScanner(folder.getRoot().getAbsolutePath(),
                    true, context)

        when:
            List<String> testFiles = testProjectScanner.scanTestFiles()

        then:
            testFiles.size() == 3
            testFiles.contains(testProject.getAbsolutePath())
            testFiles.contains(subProject.getAbsolutePath())
            testFiles.contains(subProject2.getAbsolutePath())
    }

    def 'Test File Pattern'(boolean recursive, String expectedPattern) {
        given:
            TestProjectScanner testProjectScanner = new TestProjectScanner(null, recursive, null)

        expect:
            expectedPattern == testProjectScanner.getFilePattern()

        where:
            recursive   | expectedPattern
            true        | '**/*.prj'
            false       | '*.prj'
    }

    def 'Test Input Dir Does Not Exists'() {
        given:
            TestProjectScanner testProjectScanner = new TestProjectScanner('/no/valid/path',
                    false, context)

        when:
            testProjectScanner.scanTestFiles()

        then:
            thrown IllegalStateException
    }
}
