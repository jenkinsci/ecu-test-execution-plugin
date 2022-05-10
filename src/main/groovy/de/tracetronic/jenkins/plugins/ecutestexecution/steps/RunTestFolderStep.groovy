package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import com.google.common.collect.ImmutableSet
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.TestPackageBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.builder.TestProjectBuilder
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.AnalysisConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.ExecutionConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PackageConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.configs.TestConfig
import de.tracetronic.jenkins.plugins.ecutestexecution.model.TestResult
import de.tracetronic.jenkins.plugins.ecutestexecution.scan.TestPackageScanner
import de.tracetronic.jenkins.plugins.ecutestexecution.scan.TestProjectScanner
import de.tracetronic.jenkins.plugins.ecutestexecution.util.ValidationUtil
import hudson.EnvVars
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.FormValidation
import hudson.util.IOUtils
import hudson.util.ListBoxModel
import org.jenkinsci.plugins.workflow.steps.StepContext
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.steps.StepExecution
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.DataBoundSetter
import org.kohsuke.stapler.QueryParameter

import javax.annotation.Nonnull

/**
 * Step providing the execution of ECU-TEST packages and projects inside a folder.
 * Please note that this step, in its current state, is not suitable for distributed systems and containerized ECU-TEST
 * versions (see README for an explanation).
 */
class RunTestFolderStep extends RunTestStep {
    /**
    * Defines the default {@link ScanMode}.
    */
    protected static final ScanMode DEFAULT_SCAN_MODE = ScanMode.PACKAGES_AND_PROJECTS;
    // Scan settings
    @Nonnull
    private ScanMode scanMode = DEFAULT_SCAN_MODE;
    private boolean recursiveScan;
    private boolean failFast = true;
    // Test settings
    @Nonnull
    private PackageConfig packageConfig
    @Nonnull
    private AnalysisConfig analysisConfig

    @DataBoundConstructor
    RunTestFolderStep(String testCasePath) {
        super(testCasePath)
        this.packageConfig = new PackageConfig([])
        this.analysisConfig = new AnalysisConfig()
    }

    @Nonnull
    ScanMode getScanMode() {
        return scanMode;
    }

    @DataBoundSetter
    void setScanMode(@Nonnull final ScanMode scanMode) {
        this.scanMode = scanMode;
    }

    boolean isRecursiveScan() {
        return recursiveScan;
    }

    @DataBoundSetter
    void setRecursiveScan(final boolean recursiveScan) {
        this.recursiveScan = recursiveScan;
    }

    boolean isFailFast() {
        return failFast;
    }

    @DataBoundSetter
    void setFailFast(final boolean failFast) {
        this.failFast = failFast;
    }

    @Nonnull
    PackageConfig getPackageConfig() {
        return packageConfig
    }

    @DataBoundSetter
    void setPackageConfig(PackageConfig packageConfig) {
        this.packageConfig = packageConfig ?: new PackageConfig([])
    }

    @Nonnull
    AnalysisConfig getAnalysisConfig() {
        return analysisConfig
    }

    @DataBoundSetter
    void setAnalysisConfig(AnalysisConfig analysisConfig) {
        this.analysisConfig = analysisConfig ?: new AnalysisConfig()
    }

    @Override
    StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context)
    }

    static class Execution extends SynchronousNonBlockingStepExecution<List<TestResult>> {

        private final transient RunTestFolderStep step

        Execution(RunTestFolderStep step, StepContext context) {
            super(context)
            this.step = step
        }

        @Override
        protected List<TestResult> run() throws Exception {
            List<TestResult> testResultList = new ArrayList<>()
            EnvVars envVars = context.get(EnvVars.class)
            String expTestCasePath = envVars.expand(step.testCasePath)
            ExecutionConfig expExecutionConfig = step.executionConfig
            TestConfig expTestConfig = step.testConfig.expand(envVars)
            PackageConfig expPackageConfig = step.packageConfig.expand(envVars)
            AnalysisConfig expAnalysisConfig = step.analysisConfig.expand(envVars)

            String testFolderPath = checkFolder(expTestCasePath)

            final List<String> pkgFiles = scanPackages(testFolderPath, context, step.scanMode, step.recursiveScan)
            final List<String> prjFiles = scanProjects(testFolderPath, context, step.scanMode, step.recursiveScan)


            pkgFiles.each { pkgFile ->
                TestPackageBuilder testPackage = new TestPackageBuilder(pkgFile, expTestConfig,
                        expExecutionConfig, context, expPackageConfig, expAnalysisConfig)
                TestResult result = testPackage.runTest()
                testResultList.add(result)
                if (result.getTestResult() == 'FAILED' && isFailFast()) {
                    return testResultList
                }
            }

            prjFiles.each { prjFile ->
                TestProjectBuilder testProject = new TestProjectBuilder(prjFile, expTestConfig,
                        expExecutionConfig, context)
                TestResult result = testProject.runTest()
                testResultList.add(result)
                if (result.getTestResult() == 'FAILED' && isFailFast()) {
                    return testResultList
                }
            }

             return testResultList
        }

        private String checkFolder(String folder)
                throws IOException, InterruptedException, IllegalArgumentException {
            if (IOUtils.isAbsolute(folder)) {
                FilePath folderPath = new FilePath(context.get(Launcher.class).getChannel(), folder)
                if (!folderPath.exists()) {
                    throw new IllegalArgumentException("ECU-TEST folder at ${folderPath.getRemote()} does not extist!")
                }
                return folderPath.getRemote()
            } else {
                throw new IllegalArgumentException("Unsupported relative paths for ECU-TEST folder '${folder}'!")
            }
        }
    }

     private static List<String> scanPackages(final String testFolder, final StepContext context,
                                              ScanMode scanMode, boolean isRecursive)
            throws IOException, InterruptedException {
        List<String> pkgFiles = new ArrayList<>()
        if (scanMode == ScanMode.PROJECTS_ONLY) {
            return pkgFiles
        }

        final TestPackageScanner scanner = new TestPackageScanner(testFolder, isRecursive, context)
        pkgFiles = scanner.scanTestFiles()
        if (pkgFiles.isEmpty()) {
            context.get(TaskListener.class).logger.println('No packages found!')
        } else {
            context.get(TaskListener.class).logger.println("Found ${pkgFiles.size()} package(s)")
        }

        return pkgFiles
    }

    private static List<String> scanProjects(final String testFolder, final StepContext context,
                                             ScanMode scanMode, boolean isRecursive)
            throws IOException, InterruptedException {
        List<String> prjFiles = new ArrayList<>()
        if (scanMode == ScanMode.PACKAGES_ONLY) {
            return prjFiles
        }

        final TestProjectScanner scanner = new TestProjectScanner(testFolder, isRecursive, context)
        prjFiles = scanner.scanTestFiles()
        if (prjFiles.isEmpty()) {
            context.get(TaskListener.class).logger.println('No projects found!')
        } else {
            context.get(TaskListener.class).logger.println("Found ${prjFiles.size()} project(s)")
        }

        return prjFiles
    }

    /**
     * Defines the modes to scan the test folder.
     */
    enum ScanMode {
        /**
         * Scan packages only.
         */
        PACKAGES_ONLY,

        /**
         * Scan projects only.
         */
        PROJECTS_ONLY,

        /**
         * Scan both packages and projects.
         */
        PACKAGES_AND_PROJECTS
    }

    /**
     * DescriptorImpl for {@link RunTestFolderStep}
     */
    @Extension
    static final class DescriptorImpl extends StepDescriptor {

        @Override
        String getFunctionName() {
            'ttRunTestFolder'
        }

        @Override
        String getDisplayName() {
            '[TT] Run an ECU-TEST test folder'
        }

        static ScanMode getDefaultScanMode() {
            return ScanMode.PACKAGES_AND_PROJECTS;
        }

        /**
         * Fills the scan mode drop-down menu.
         *
         * @return the scan mode items
         */
        ListBoxModel doFillScanModeItems() {
            final ListBoxModel items = new ListBoxModel();
            items.add('Scan for package files only', ScanMode.PACKAGES_ONLY.toString());
            items.add('Scan for project files only', ScanMode.PROJECTS_ONLY.toString());
            items.add('Scan both for package and project files', ScanMode.PACKAGES_AND_PROJECTS.toString());
            return items;
        }

        @Override
        Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Launcher.class, Run.class, EnvVars.class, TaskListener.class)
        }

        /**
         * Validates the test folder path.
         *
         * @param value the test folder path
         * @return the form validation
         */
        FormValidation doCheckTestCasePath(@QueryParameter String value) {
            return ValidationUtil.validateAbsolutePath(value)
        }
    }
}
