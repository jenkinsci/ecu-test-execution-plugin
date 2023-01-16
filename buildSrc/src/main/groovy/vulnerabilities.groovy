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
                ['credentials-1087.v16065d268466',
                 'script-security-1158.v7c1b_73a_69a_08',
                 'structs-318.va_f3ccb_729b_71',
                 'workflow-step-api-625.vd896b_f445a_f8',
                 'cyclonedx-gradle-plugin-1.4.0',
                 "spotbugs-4.7.3",
                 'guice-4.0'
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
