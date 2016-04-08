/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.gradle.testkit.runner.TaskOutcome

class PalantirDockerPluginTests extends AbstractPluginTest {

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
        buildResult.output.contains("name is a required docker configuration item.")
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
        buildResult.output.contains("name is a required docker configuration item.")
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
        buildResult.output.contains("dockerfile 'missing' does not exist.")
    }

    def 'check plugin creates a docker container with default configuration'() {
        given:
        String id = 'id1'
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:edge
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
        execCond("docker rmi -f ${id}")
    }

    def 'check plugin creates a docker container with non-standard Dockerfile name'() {
        given:
        String id = 'id2'
        temporaryFolder.newFile('foo') << """
            FROM alpine:edge
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
        execCond("docker rmi -f ${id}")
    }

    def 'check files are correctly added to docker context'() {
        given:
        String id = 'id3'
        String filename = "foo.txt"
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:edge
            MAINTAINER ${id}
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
        println exec("docker images")
        println exec("docker version")
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        // execCond("sleep1; docker rmi -f ${id}")
        println exec("docker inspect ${id}")
        StringBuffer sout = new StringBuffer(), serr = new StringBuffer()
        Process proc = "docker rmi -f ${id}".execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()
        println sout.toString()
        println serr.toString()
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

    def 'Can apply both "docker" and "docker-compose" plugins'() {
        given:
        temporaryFolder.newFile('Dockerfile') << "Foo"
        buildFile << '''
            plugins {
                id 'com.palantir.docker'
                id 'com.palantir.docker-compose'
            }

            docker {
                name 'foo'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('tasks').build()
        then:
        buildResult.task(':tasks').outcome == TaskOutcome.SUCCESS
    }

    def 'no tag task when no tags defined'() {
        given:
        String id = 'id4'
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:edge
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
        BuildResult buildResult = with('tasks').build()

        then:
        !buildResult.output.contains('dockerTag')
    }

    def 'tag and push tasks created for each tag'() {
        given:
        String id = 'id5'
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:edge
            MAINTAINER ${id}
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                tags 'latest', 'another'
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('tasks').build()

        then:
        buildResult.output.contains('dockerTagLatest')
        buildResult.output.contains('dockerTagAnother')
        buildResult.output.contains('dockerPushLatest')
        buildResult.output.contains('dockerPushAnother')
    }

    def 'running tag task creates images with specified tags'() {
        given:
        String id = 'id6'
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:edge
            MAINTAINER ${id}
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                tags 'latest', 'another'
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('dockerTag').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        buildResult.task(':dockerTag').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}:latest") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}:another") == "'${id}'\n"
        execCond("docker rmi -f ${id}")
        execCond("docker rmi -f ${id}:another")
    }
}
