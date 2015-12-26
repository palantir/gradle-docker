package com.palantir.gradle.docker

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DockerComposePluginTests extends AbstractPluginTest {

    def 'Generates docker-compose.yml from template with version strings replaced'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        temporaryFolder.newFile("docker-compose.yml.template") << '''
            service1:
              image: 'repository/service1:{{com.google.guava:guava}}'
            service2:
              image: 'repository/service2:{{org.slf4j:slf4j-api}}'
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }

            repositories {
                jcenter()
            }

            dependencies {
                docker 'io.dropwizard:dropwizard-jackson:0.8.2'
                  // transitive dependencies: com.google.guava:guava:18.0, org.slf4j:slf4j-api:1.7.10
                docker 'com.google.guava:guava:17.0'  // should bump to 18.0 via the above
            }

        '''.stripIndent()

        when:
        BuildResult buildResult = with('generateDockerCompose', "--stacktrace").build()
        then:
        buildResult.task(':generateDockerCompose').outcome == TaskOutcome.SUCCESS
        def dockerComposeText = temporaryFolder.root.toPath().resolve("docker-compose.yml").text
        dockerComposeText.contains("repository/service1:18.0")
        dockerComposeText.contains("repository/service2:1.7.10")
    }

    def 'Fails if docker-compose.yml.template has unmatched version tokens'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        temporaryFolder.newFile("docker-compose.yml.template") << '''
            service1:
              image: 'repository/service1:{{foo:bar}}'
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }

            repositories {
                jcenter()
            }

            dependencies {
                docker 'com.google.guava:guava:17.0'  // should bump to 18.0 via the above
            }

        '''.stripIndent()

        when:
        BuildResult buildResult = with('generateDockerCompose', '--stacktrace').buildAndFail()
        then:
        buildResult.output.contains("Failed to resolve Docker dependencies declared in")
        buildResult.output.contains("{{foo:bar}}")
    }

    def 'docker-compose template and file can have custom locations'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        new File(temporaryFolder.newFolder("templates"), "customTemplate.yml") << '''
            nothing
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }

            repositories {
                jcenter()
            }

            dockerCompose {
                template 'templates/customTemplate.yml'
                dockerComposeFile 'compose-files/customDockerCompose.yml'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('generateDockerCompose', "--stacktrace").build()
        then:
        buildResult.task(':generateDockerCompose').outcome == TaskOutcome.SUCCESS
        temporaryFolder.root.toPath().resolve("compose-files").resolve("customDockerCompose.yml").toFile().exists()
    }

    def 'Fails if template is configured but does not exist'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }

            dockerCompose {
                template 'templates/customTemplate.yml'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('generateDockerCompose').buildAndFail()
        then:
        buildResult.output.contains("Could not find specified template file")
    }
}
