import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class FilterTask extends DefaultTask {

    @Input
    abstract Property<Float> getMinimumScore()

    @TaskAction
    def filter() {
        def directDeps =
                ['credentials-',
                 'script-security-',
                 'structs-',
                 'workflow-step-api-'
                ]

        def minScore = minimumScore.get()
        def jsonSlurper = new JsonSlurper()

        File file = new File("build/reports/dependency-check-report.json")

        def jsonObj = jsonSlurper.parse(file)
        def vulnerableDeps = jsonObj.dependencies

        def vulnerableTopLevelDeps = vulnerableDeps.findAll {
            it -> directDeps.any {name -> it.fileName.contains(name)}
        }

        vulnerableTopLevelDeps.each {
            dep ->
                def vulnerabilities = dep.vulnerabilities
                println "Vulnerable Top-Level Dep.: " + dep.fileName
                vulnerabilities.each {
                   vul ->
                       def cvss = vul.cvssv3?: vul.cvssv2
                       def vulName = vul.name
                       def score = cvss.baseScore?: cvss.score
                       if (score >=  minScore) {
                           println "Name: " + vulName
                           println "Score: " + score
                       }
                }

        }
    }
}
