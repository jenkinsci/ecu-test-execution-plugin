package de.tracetronic.jenkins.plugins.ecutestexecution.steps

import de.tracetronic.jenkins.plugins.ecutestexecution.configs.PublishConfig
import hudson.Launcher
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tasks.junit.TestResult
import hudson.tasks.junit.TestResultAction
import org.jenkinsci.plugins.workflow.steps.StepContext
import spock.lang.Specification

class ProvideUnitReportsStepExecutionTest extends Specification {
    ProvideUnitReportsStep step = new ProvideUnitReportsStep()
    // Mocks
    StepContext context = GroovyMock(StepContext)
    Run run = GroovyMock(Run)
    TaskListener listener = GroovyMock(TaskListener)
    Launcher launcher = GroovyMock(Launcher)
    PrintStream logger = GroovyMock(PrintStream)

    def setup() {
        context.get(Run.class) >> run
        context.get(TaskListener.class) >> listener
        context.get(Launcher.class) >> launcher
        listener.getLogger() >> logger
    }

    def "Test handling of UnsupportedOperationException"() {
        given:
            ProvideUnitReportsStep.Execution exec = Spy(constructorArgs:[step, context], {
                getUnitReportFilePaths() >> { throw new UnsupportedOperationException("Unsupported") }
            })
        when:
            exec.run()
        then:
        1 * run.setResult(Result.UNSTABLE)
        1 * logger.println("Providing ${step.outDirName} failed!")
        1 * listener.error("Unsupported")
        0 * exec.parseReportFiles(_)
    }

    def "Test fail on empty TestResult"() {
        given:
            TestResult result = GroovyStub {
                getFailCount() >> 0
                getTotalCount() >> 0
            }
        and:
            ProvideUnitReportsStep.Execution exec = Spy(constructorArgs:[step, context], {
                getUnitReportFilePaths() >> _
                parseReportFiles(_) >> result
            })
        when:
            exec.run()
        then:
            1 * run.setResult(Result.FAILURE)
            1 * logger.println("No unit test results found.")
            1 * logger.println("Providing ${step.outDirName} failed!")
            1 * listener.error("Build result set to ${Result.FAILURE.toString()} due to missing test results. Adjust AllowMissing step property if this is not intended.")
            0 * exec.addResultsToRun(_)
    }

    def "Test success on empty TestResult when allowMissing is true"() {
        given:
            TestResult result = GroovyStub {
                getFailCount() >> 0
                getTotalCount() >> 0
            }
        and:
            def publishConf = new PublishConfig()
            publishConf.setAllowMissing(true)
            step.setPublishConfig(publishConf)
        and:
            ProvideUnitReportsStep.Execution exec = Spy(constructorArgs:[step, context], {
                getUnitReportFilePaths() >> _
                parseReportFiles(_) >> result
                addResultsToRun(_) >> _
            })
        when:
            exec.run()
        then:
            0 * run.setResult(Result.FAILURE)
            0 * logger.println("Successfully added test results to Jenkins.")
            1 * logger.println("No unit test results found.")
    }

    def "Test addResultsToRun with existing action"() {
        given:
            def exec = new ProvideUnitReportsStep.Execution(step, context)
            def testResult = new TestResult()
        and:
            def action = GroovyMock(TestResultAction)
            run.getAction(TestResultAction.class) >> action
        when:
            exec.addResultsToRun(testResult)
        then:
            1 * action.mergeResult(testResult, listener)
            0 * run.addAction(action)
    }

    def "Test addResultsToRun with missing action"() {
        given:
            def exec = new ProvideUnitReportsStep.Execution(step, context)
            def testResult = new TestResult()
        and:
            TestResultAction action = GroovyMock(global: true)
        when:
            exec.addResultsToRun(testResult)
        then:
            1 * run.addAction(_)
    }

    def "Test run fails when reaching failedThreshold"() {
        given:
            TestResult result = GroovyStub {
                getFailCount() >> 6
                getTotalCount() >> 100
                getPassCount() >> 90
                getSkipCount() >> 4
            }
        and:
            step.setFailedThreshold(5.0)
        and:
            ProvideUnitReportsStep.Execution exec = Spy(constructorArgs:[step, context], {
                getUnitReportFilePaths() >> _
                parseReportFiles(_) >> result
                addResultsToRun(_) >> _
            })
        when:
            exec.run()
        then:
            1 * logger.println("Found 100 test result(s) in total: #Passed: 90, #Failed: 6, #Skipped: 4")
            1 * logger.println("Successfully added test results to Jenkins.")
            1 * logger.println("Build result set to ${Result.FAILURE.toString()} due to percentage of failed tests is higher than the configured threshold.")
            1 * run.setResult(Result.FAILURE)
    }

    def "Test run unstable when reaching unstableThreshold"() {
        given:
            TestResult result = GroovyStub {
                getFailCount() >> 6
                getTotalCount() >> 100
            }
        and:
            step.setUnstableThreshold(5.0)
        and:
            ProvideUnitReportsStep.Execution exec = Spy(constructorArgs:[step, context], {
                getUnitReportFilePaths() >> _
                parseReportFiles(_) >> result
                addResultsToRun(_) >> _
            })
        when:
            exec.run()
        then:
            1 * logger.println("Successfully added test results to Jenkins.")
            1 * logger.println("Build result set to ${Result.UNSTABLE.toString()} due to percentage of failed tests is higher than the configured threshold.")
            1 * run.setResult(Result.UNSTABLE)
    }

    def "Test run unstable when step has warnings"() {
        given:
            TestResult result = GroovyStub {
                getTotalCount() >> 100
            }
        and:
            step.hasWarnings = true
        and:
            ProvideUnitReportsStep.Execution exec = Spy(constructorArgs:[step, context], {
                getUnitReportFilePaths() >> _
                parseReportFiles(_) >> result
                addResultsToRun(_) >> _
            })
        when:
            exec.run()
        then:
            1 * logger.println("Successfully added test results to Jenkins.")
            1 * logger.println("Build result set to ${Result.UNSTABLE.toString()} due to warnings.")
            1 * run.setResult(Result.UNSTABLE)
    }
}
