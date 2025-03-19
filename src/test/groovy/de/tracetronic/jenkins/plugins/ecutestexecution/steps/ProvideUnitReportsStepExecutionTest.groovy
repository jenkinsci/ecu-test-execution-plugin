package de.tracetronic.jenkins.plugins.ecutestexecution.steps

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

    def "Test fail on empty TestResult"() {
        given:
            def exec = GroovySpy(ProvideUnitReportsStep.Execution, constructorArgs:[step, context])
            exec.getUnitReportFilePaths() >> _
            exec.parseReportFiles(_) >> new TestResult()
        when:
            exec.run()
        then:
            1 * run.setResult(Result.FAILURE)
            1 * logger.println("Providing ${step.outDirName} failed!")
            1 * listener.error("Build result set to ${Result.FAILURE.toString()} due to missing ${step.outDirName}. Adjust AllowMissing step property if this is not intended.")
            0 * exec.addResultsToRun(_)
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
            def testResult = GroovyMock(TestResult)
            testResult.getFailCount() >> 6
            testResult.getTotalCount() >> 100
        and:
            step.setFailedThreshold(5.0)
        and:
            def exec = GroovySpy(ProvideUnitReportsStep.Execution, constructorArgs:[step, context])
            exec.getUnitReportFilePaths() >> _
            exec.parseReportFiles(_) >> testResult
            exec.addResultsToRun(testResult) >> _
        when:
            exec.run()
        then:
            1 * logger.println("Successfully added ${step.outDirName} to Jenkins.")
            1 * run.setResult(Result.FAILURE)
    }

    def "Test run unstable when reaching unstableThreshold"() {
        given:
            def result = GroovyMock(TestResult)
            result.getFailCount() >> 5
            result.getTotalCount() >> 10
        and:
            def step = new ProvideUnitReportsStep()
            step.setUnstableThreshold(5.0)
        and:
            def exec = GroovySpy(ProvideUnitReportsStep.Execution, constructorArgs:[step, context])
            exec.getUnitReportFilePaths() >> _
            exec.parseReportFiles(_) >> result
            exec.addResultsToRun(_) >> _
        when:
            exec.run()
        then:
            1 * logger.println("Successfully added ${step.outDirName} to Jenkins.")
            1 * run.setResult(Result.UNSTABLE)
    }
}
