/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.docker

import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import spock.lang.Specification

class PalantirDockerPluginTests extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile
    List<File> pluginClasspath


    def 'fail when missing docker configuration'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.docker'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('docker').buildAndFail()

        then:
        buildResult.standardError.contains("name is a required docker configuration item.")
    }

    def 'fail with empty container name'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.docker'
            }
            docker {
                name ''
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('docker').buildAndFail()

        then:
        buildResult.standardError.contains("name is a required docker configuration item.")
    }

    def 'fail with missing dockerfile'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.docker'
            }
            docker {
                name 'test'
                dockerfile 'missing'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('docker').buildAndFail()

        then:
        buildResult.standardError.contains("dockerfile 'missing' does not exist.")
    }

    def 'check plugin creates a docker container with default configuration'() {
        given:
        String id = UUID.randomUUID().toString()
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER ${id}
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        exec("docker rmi ${id}")
    }

    def 'check plugin creates a docker container with non-standard Dockerfile name'() {
        given:
        String id = UUID.randomUUID().toString()
        temporaryFolder.newFile('foo') << """
            FROM alpine:3.2
            MAINTAINER ${id}
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                dockerfile 'foo'
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        exec("docker rmi ${id}")
    }

    def 'check files are correctly added to docker context'() {
        given:
        String id = UUID.randomUUID().toString()
        String filename = "foo.txt"
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER ${id}
            ADD ${filename} /tmp/
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                files "${filename}"
            }
        """.stripIndent()
        new File(projectDir, filename).createNewFile()
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        exec("docker rmi ${id}")
    }

    def 'Publishes "docker" dependencies via "docker" component'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        buildFile << '''
            plugins {
                id 'java'
                id 'maven-publish'
                id 'com.palantir.docker'
            }

            docker {
                name 'foo'
            }

            group 'testgroup'
            version '2.3.4'

            dependencies {
                // Should *not* get published to the docker maven publication
                compile 'com.google.guava:guava:18.0'

                // Should get published to the docker maven publication
                docker 'foogroup:barmodule:0.1.2'
                docker project(":")  // Resolves to "testgroup:junit123..:2.3.4"
            }

            publishing {
                publications {
                    dockerPublication(MavenPublication) {
                        from components.docker
                        artifactId project.name + "-docker"
                    }
                    javaPublication(MavenPublication) {
                        from components.java
                    }
                }
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('publishToMavenLocal').build()
        then:
        buildResult.task(':publishToMavenLocal').outcome == TaskOutcome.SUCCESS
        def publishFolder = new DefaultLocalMavenRepositoryLocator(
            new DefaultMavenSettingsProvider(new DefaultMavenFileLocations())).localMavenRepository.toPath()
            .resolve("testgroup")

        // Check java publication has the right dependencies
        def javaPublishFolder = publishFolder.resolve(temporaryFolder.root.name).resolve("2.3.4")
        def javaPomFile = javaPublishFolder.resolve(temporaryFolder.root.name + "-2.3.4.pom")
        def javaPom = javaPomFile.toFile().text
        ["com.google.guava", "guava", "18.0"].each { javaPom.contains(it) }
        ["foogroup", "barmodule"].each { !javaPom.contains(it) }

        // Check docker publication has the right dependencies
        def dockerPublishFolder = publishFolder
            .resolve(temporaryFolder.root.name + "-docker")
            .resolve("2.3.4")
        def zipFile = dockerPublishFolder.resolve(temporaryFolder.root.name + "-docker-2.3.4.zip")
        zipFile.toFile().exists()
        def dockerPomFile = dockerPublishFolder.resolve(temporaryFolder.root.name + "-docker-2.3.4.pom")
        def dockerPom = dockerPomFile.toFile().text
        ["foogroup", "barmodule", "0.1.2", temporaryFolder.root.name, "2.3.4"].each { dockerPom.contains(it) }
        !dockerPom.contains("guava")
    }

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
                id 'com.palantir.docker'
            }

            repositories {
                jcenter()
            }

            docker {
                name 'foo'
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
                id 'com.palantir.docker'
            }

            repositories {
                jcenter()
            }

            docker {
                name 'foo'
            }

            dependencies {
                docker 'com.google.guava:guava:17.0'  // should bump to 18.0 via the above
            }

        '''.stripIndent()

        when:
        BuildResult buildResult = with('generateDockerCompose', '--stacktrace').buildAndFail()
        then:
        buildResult.standardError.contains("Failed to resolve Docker dependencies mention in")
        buildResult.standardError.contains("{{foo:bar}}")
    }

    def 'docker-compose template and file can have custom locations'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        new File(temporaryFolder.newFolder("templates"), "customTemplate.yml") << '''
            nothing
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker'
            }

            repositories {
                jcenter()
            }

            docker {
                name 'foo'
                dockerComposeTemplate 'templates/customTemplate.yml'
                dockerComposeFile 'compose-files/customDockerCompose.yml'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('generateDockerCompose', "--stacktrace").build()
        then:
        buildResult.task(':generateDockerCompose').outcome == TaskOutcome.SUCCESS
        temporaryFolder.root.toPath().resolve("compose-files").resolve("customDockerCompose.yml").toFile().exists()
    }

    def 'Fails if dockerComposeTemplate is configured but does not exist'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        buildFile << '''
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name 'foo'
                dockerComposeTemplate 'templates/customTemplate.yml'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('generateDockerCompose').buildAndFail()
        then:
        buildResult.standardError.contains("Could not find specified dockerComposeTemplate file")
    }

    private GradleRunner with(String... tasks) {
        GradleRunner.create()
            .withPluginClasspath(pluginClasspath)
            .withProjectDir(projectDir)
            .withArguments(tasks)
    }

    private String exec(String task) {
        StringBuffer sout = new StringBuffer(), serr = new StringBuffer()
        Process proc = task.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()
        return sout.toString()
    }

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile('build.gradle')

        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        pluginClasspath = pluginClasspathResource.readLines()
            .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
            .collect { new File(it) }
    }

}
