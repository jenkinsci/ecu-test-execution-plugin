package de.tracetronic.jenkins.plugins.ecutestexecution

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.Rule
import org.jvnet.hudson.test.GroovyJenkinsRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared
import spock.lang.Unroll

class DocsExamplesContainerTest extends ContainerTest  {

    private static final Logger LOGGER = LoggerFactory.getLogger(ETContainerTest.class)

    @Rule
    protected GroovyJenkinsRule jenkins = new GroovyJenkinsRule()

    protected GenericContainer etContainer = getETContainer()

    @Shared
    def codeBlocks = extractCodeBlocks("./docs/AdvancedUsage.md")

    GenericContainer getETContainer() {
            return new GenericContainer<>(ET_V2_IMAGE_NAME)
                    .withExposedPorts(ET_PORT)
                    .withClasspathResourceMapping("workspace/.workspace", "${ET_WS_PATH}/.workspace",
                            BindMode.READ_ONLY)
                    .withClasspathResourceMapping("workspace/Configurations",
                            "${ET_WS_PATH}/Configurations", BindMode.READ_ONLY)
                    .withClasspathResourceMapping("workspace/Packages", "${ET_WS_PATH}/Packages",
                            BindMode.READ_ONLY)
                    .withClasspathResourceMapping("workspace/UserPyModules", "${ET_WS_PATH}/UserPyModules",
                            BindMode.READ_ONLY)
                    .withClasspathResourceMapping("workspace/localsettings.xml", "${ET_WS_PATH}/localsettings.xml",
                            BindMode.READ_ONLY)
                    .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                    .waitingFor(Wait.forHttp("/api/v2/live"))
    }

    List<String> extractCodeBlocks(filepath) {
        def codeBlocks = []
        def inTestableBlock = false
        def currentBlock = new StringBuilder()

        new File(filepath).eachLine { line ->
            if (line.contains('node {')) {
                inTestableBlock = true
            }

            if (inTestableBlock && line.contains('```')) {
                codeBlocks.add(currentBlock.toString().trim())
                currentBlock.delete(0, currentBlock.length())
                inTestableBlock = false
            }

            if (inTestableBlock) {
                currentBlock.append(line).append('\n')
            }
        }

        return codeBlocks
    }

    @Unroll
    def "Run example #index"() {
        given:
            def code = codeBlocks[index]
            code = code.replaceAll(/(?ms)node \{(.*)\}/, "node {withEnv(['ET_API_HOSTNAME=${etContainer.host}', 'ET_API_PORT=${etContainer.getMappedPort(ET_PORT)}']) { \$1}}")
            WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-${index}")
            job.setDefinition(new CpsFlowDefinition(code, true))

        expect:
            WorkflowRun run = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0))

        where:
            index << (0..<codeBlocks.size())
    }
}
