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
        file('Dockerfile') << """
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
        execCond("docker rmi -f ${id}")
    }

    def 'check plugin creates a docker container with non-standard Dockerfile name'() {
        given:
        String id = 'id2'
        file('foo') << """
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
        execCond("docker rmi -f ${id}")
    }

    def 'check files are correctly added to docker context'() {
        given:
        String id = 'id3'
        String filename = "foo.txt"
        file('Dockerfile') << """
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
        execCond("docker rmi -f ${id}") || true
    }

    def 'Publishes "docker" dependencies via "docker" component'() {
        given:
        file('Dockerfile') << "Foo"
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
        def javaPublishFolder = publishFolder.resolve(projectDir.name).resolve("2.3.4")
        def javaPomFile = javaPublishFolder.resolve(projectDir.name + "-2.3.4.pom")
        def javaPom = javaPomFile.toFile().text
        ["com.google.guava", "guava", "18.0"].each { javaPom.contains(it) }
        ["foogroup", "barmodule"].each { !javaPom.contains(it) }

        // Check docker publication has the right dependencies
        def dockerPublishFolder = publishFolder
            .resolve(projectDir.name + "-docker")
            .resolve("2.3.4")
        def zipFile = dockerPublishFolder.resolve(projectDir.name + "-docker-2.3.4.zip")
        zipFile.toFile().exists()
        def dockerPomFile = dockerPublishFolder.resolve(projectDir.name + "-docker-2.3.4.pom")
        def dockerPom = dockerPomFile.toFile().text
        ["foogroup", "barmodule", "0.1.2", projectDir.name, "2.3.4"].each { dockerPom.contains(it) }
        !dockerPom.contains("guava")
    }

    def 'Can apply both "docker" and "docker-compose" plugins'() {
        given:
        file('Dockerfile') << "Foo"
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
        file('Dockerfile') << """
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
        BuildResult buildResult = with('tasks').build()

        then:
        !buildResult.output.contains('dockerTag')
    }

    def 'tag and push tasks created for each tag'() {
        given:
        String id = 'id5'
        file('Dockerfile') << """
            FROM alpine:3.2
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
        file('Dockerfile') << """
            FROM alpine:3.2
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

    def 'build args are correctly processed'() {
        given:
        String id = 'id7'
        file('Dockerfile') << '''
            FROM alpine:3.2
            ARG BUILD_ARG_NO_DEFAULT
            ARG BUILD_ARG_WITH_DEFAULT=defaultBuildArg
            ENV ENV_BUILD_ARG_NO_DEFAULT $BUILD_ARG_NO_DEFAULT
            ENV ENV_BUILD_ARG_WITH_DEFAULT $BUILD_ARG_WITH_DEFAULT
        '''.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                buildArgs([BUILD_ARG_NO_DEFAULT: 'gradleBuildArg', BUILD_ARG_WITH_DEFAULT: 'gradleOverrideBuildArg'])
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Config.Env}}' ${id}").contains('ENV_BUILD_ARG_NO_DEFAULT=gradleBuildArg')
        exec("docker inspect --format '{{.Config.Env}}' ${id}").contains('BUILD_ARG_WITH_DEFAULT=gradleOverrideBuildArg')
        execCond("docker rmi -f ${id}") || true
    }

    def 'base image is pulled when "pull" parameter is set'() {
        given:
        String id = 'id8'
        file('Dockerfile') << '''
            FROM alpine:3.2
        '''.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                pull true
            }
        """.stripIndent()

        when:
        execCond("docker pull alpine:3.2")
        BuildResult buildResult = with('-i', 'docker').build()

        then:
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        buildResult.output.contains 'Pulling from library/alpine'
        execCond("docker rmi -f ${id}") || true
    }

    def 'when no files are specified, then all files from the project directory are added to the docker context'() {
        given:
        String id = 'id9'
        String filename = "bar.txt"
        file('Dockerfile') << """
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
            }
        """.stripIndent()
        createFile(filename)
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        execCond("docker rmi -f ${id}") || true
    }

    def 'outputs from default distribution locations are added to the docker context'() {
        given:
        String id = 'id11'
        File distributions = temporaryFolder.newFolder('build', 'distributions');
        new File(distributions, 'dist.txt').createNewFile();

        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER id
            ADD build/distributions/dist.txt /tmp/
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
        execCond("docker rmi -f ${id}") || true
    }

    def 'explicitly adding everything does not crash due to recursive overflow'() {
        given:
        String id = 'id12'
        File distributions = temporaryFolder.newFolder('build', 'distributions');
        new File(distributions, 'dist.txt').createNewFile();

        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER id
            ADD build/distributions/dist.txt /tmp/
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }
            docker {
                name '${id}'
                files '.'
            }
        """.stripIndent()
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        execCond("docker rmi -f ${id}") || true
    }

    def 'check if compute name replaces the name correctly'() {
        expect:
        PalantirDockerPlugin.computeName(name, tag) == result

        where:
        name                |  tag         | result
        "v1"                | "latest"     | "v1:latest"
        "v1:1"              | "latest"     | "v1:latest"
        "host/v1"           | "latest"     | "host/v1:latest"
        "host/v1:1"         | "latest"     | "host/v1:latest"
        "host:port/v1"      | "latest"     | "host:port/v1:latest"
        "host:port/v1:1"    | "latest"     | "host:port/v1:latest"
    }
}

