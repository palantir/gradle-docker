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
import spock.lang.Ignore

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
                dockerfile project.file("foo")
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
        execCond("docker rmi -f ${id}")
    }

    def 'check multiarch'() {
        given:
        String id = 'id4'
        String filename = "foo.txt"
        file('Dockerfile') << """
            FROM alpine
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
                buildx true
                load true
                platform 'linux/arm64'
            }
        """.stripIndent()
        new File(projectDir, filename).createNewFile()
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Architecture}}' ${id}") == "'arm64'\n"
        execCond("docker rmi -f ${id}")
    }
    // Gradle explicitly disallows the test case, fails with the following:
    //Could not determine the dependencies of task ':publishDockerPublicationPublicationToMavenLocal'.
    //> Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.
    @Ignore
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
                tags 'latest', 'another', 'withTaskName@2.0', 'newImageName@${id}-new:latest'
                tag 'withTaskNameByTag', '${id}:new-latest'
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('tasks').build()

        then:
        buildResult.output.contains('dockerTagLatest')
        buildResult.output.contains('dockerTagAnother')
        buildResult.output.contains('dockerTagWithTaskName')
        buildResult.output.contains('dockerTagNewImageName')
        buildResult.output.contains('dockerTagWithTaskNameByTag')
        buildResult.output.contains('dockerPushLatest')
        buildResult.output.contains('dockerPushAnother')
        buildResult.output.contains('dockerPushWithTaskName')
        buildResult.output.contains('dockerPushNewImageName')
        buildResult.output.contains('dockerPushWithTaskNameByTag')
    }

    def 'does not throw if name is configured after evaluation phase'() {
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
                tags 'latest', 'another', 'withTaskName@2.0', 'newImageName@${id}-new:latest'
                tag 'withTaskNameByTag', '${id}:new-latest'
            }

            afterEvaluate {
                docker.name = '${id}'
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
        exec("docker inspect --format '{{.Author}}' ${id}:2.0") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}-new:latest") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}:new-latest") == "'${id}'\n"
        execCond("docker rmi -f ${id}")
        execCond("docker rmi -f ${id}:another")
        execCond("docker rmi -f ${id}:latest")
        execCond("docker rmi -f ${id}:2.0")
        execCond("docker rmi -f ${id}-new:latest")
        execCond("docker rmi -f ${id}:new-latest")
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
                name 'fake-service-name'
                tags 'latest', 'another', 'withTaskName@2.0', 'newImageName@${id}-new:latest'
                tag 'withTaskNameByTag', '${id}:new-latest'
            }

            afterEvaluate {
                docker.name = '${id}'
            }

            task printInfo {
                doLast {
                    println "LATEST: \${tasks.dockerTagLatest.commandLine}"
                    println "ANOTHER: \${tasks.dockerTagAnother.commandLine}"
                    println "WITH_TASK_NAME: \${tasks.dockerTagWithTaskName.commandLine}"
                    println "NEW_IMAGE_NAME: \${tasks.dockerTagNewImageName.commandLine}"
                    println "WITH_TASK_NAME_BY_TAG: \${tasks.dockerTagWithTaskNameByTag.commandLine}"
                }
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('dockerTag', 'printInfo').build()

        then:
        buildResult.output.contains("LATEST: [docker, tag, ${id}, ${id}:latest]")
        buildResult.output.contains("ANOTHER: [docker, tag, ${id}, ${id}:another]")
        buildResult.output.contains("WITH_TASK_NAME: [docker, tag, ${id}, ${id}:2.0]")
        buildResult.output.contains("NEW_IMAGE_NAME: [docker, tag, ${id}, ${id}-new:latest]")
        buildResult.output.contains("WITH_TASK_NAME_BY_TAG: [docker, tag, ${id}, ${id}:new-latest]")
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        buildResult.task(':dockerTag').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}:latest") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}:another") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}:2.0") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}-new:latest") == "'${id}'\n"
        exec("docker inspect --format '{{.Author}}' ${id}:new-latest") == "'${id}'\n"
        execCond("docker rmi -f ${id}")
        execCond("docker rmi -f ${id}:latest")
        execCond("docker rmi -f ${id}:another")
        execCond("docker rmi -f ${id}:2.0")
        execCond("docker rmi -f ${id}-new:latest")
        execCond("docker rmi -f ${id}:new-latest")
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
        execCond("docker rmi -f ${id}")
    }

    def 'rebuilding an image does it from scratch when "noCache" parameter is set'() {
        given:
        String id = 'id66'
        String filename = "bar.txt"
        file('Dockerfile') << """
            FROM alpine:3.2
            ADD ${filename} /tmp/
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                files "${filename}"
                noCache true
            }
        """.stripIndent()
        createFile(filename)

        when:
        BuildResult buildResult1 = with('--info', 'docker').build()
        def imageID1 = exec("docker inspect --format=\"{{.Id}}\" ${id}")
        BuildResult buildResult2 = with('--info', 'docker').build()
        def imageID2 = exec("docker inspect --format=\"{{.Id}}\" ${id}")

        then:
        buildResult1.task(':docker').outcome == TaskOutcome.SUCCESS
        buildResult2.task(':docker').outcome == TaskOutcome.SUCCESS
        imageID1 != imageID2
        execCond("docker rmi -f ${id}")
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
        execCond("docker rmi -f ${id}")
    }

    def 'can build docker with network mode configured'() {
        given:
        String id = 'id11'
        file('Dockerfile') << '''
            FROM alpine:3.2
            RUN curl localhost:404
        '''.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                // this should trigger the error because this is invalid option
                // thus it is possible to validate the --network was set correctly
                network 'foobar'
            }
        """.stripIndent()

        when:
        BuildResult buildResult = with('-i', 'docker').buildAndFail()

        then:
        buildResult.task(':docker').outcome == TaskOutcome.FAILED
        buildResult.output.contains('network foobar not found') or(
            buildResult.output.contains('No such network: foobar')
        )
        execCond("docker rmi -f ${id}")
    }

    def 'can add files from project directory to build context'() {
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
                files "bar.txt"
            }
        """.stripIndent()
        createFile(filename)
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Author}}' ${id}") == "'${id}'\n"
        execCond("docker rmi -f ${id}")
    }

    def 'when adding a project-dir file and a Tar file, then they both end up (unzipped) in the docker image'() {
        given:
        String id = 'id10'
        createFile('from_project')
        createFile('from_tgz')

        file('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER id
            ADD foo.tgz /tmp/
            ADD from_project /tmp/
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            task myTgz(type: Tar) {
                destinationDir project.buildDir
                baseName 'foo'
                extension = 'tgz'
                compression = Compression.GZIP
                into('.') {
                    from 'from_tgz'
                }
            }

            docker {
                name '${id}'
                files tasks.myTgz.outputs, 'from_project'
            }
        """.stripIndent()
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':myTgz').outcome == TaskOutcome.SUCCESS
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        execCond("docker rmi -f ${id}")
    }

    def 'can build Docker image from standard Gradle distribution plugin'() {
        given:
        String id = 'id11'

        file('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER id
            ADD . /tmp/
        """.stripIndent()

        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test { public static void main(String[] args) {} }
        '''.stripIndent()

        buildFile << """
            plugins {
                id 'com.palantir.docker'
                id 'java'
                id 'application'
            }
            mainClassName = "test.Test"

            docker {
                name '${id}'
                files tasks.distTar.outputs
            }
        """.stripIndent()
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':distTar').outcome == TaskOutcome.SUCCESS
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        execCond("docker rmi -f ${id}")
    }

    def 'check labels are correctly applied to image'() {
        given:
        String id = 'id10'
        file('Dockerfile') << """
            FROM alpine:3.2
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                labels 'test-label': 'test-value', 'another.label': 'another.value'
            }
        """.stripIndent()
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        exec("docker inspect --format '{{.Config.Labels}}' ${id}").contains("test-label")
        execCond("docker rmi -f ${id}")
    }

    def 'fail with bad label key character'() {
        given:
        file('Dockerfile') << """
            FROM alpine:3.2
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name 'test-bad-labels'
                labels 'test_label': 'test_value'
            }
        """.stripIndent()
        when:
        BuildResult buildResult = with('docker').buildAndFail()

        then:
        buildResult.output.contains("Docker label 'test_label' contains illegal characters. Label keys " +
            "must only contain lowercase alphanumberic, `.`, or `-` characters (must match " +
            "^[a-z0-9.-]*\$).")
    }

    def 'check if compute name replaces the name correctly'() {
        expect:
        PalantirDockerPlugin.computeName(name, tag) == result

        where:
        name             | tag      | result
        "v1"             | "latest" | "v1:latest"
        "v1:1"           | "latest" | "v1:latest"
        "host/v1"        | "latest" | "host/v1:latest"
        "host/v1:1"      | "latest" | "host/v1:latest"
        "host:port/v1"   | "latest" | "host:port/v1:latest"
        "host:port/v1:1" | "latest" | "host:port/v1:latest"
        "v1"             | "name@latest" | "v1:latest"
        "v1:1"           | "name@latest" | "v1:latest"
        "host/v1"        | "name@latest" | "host/v1:latest"
        "host/v1:1"      | "name@latest" | "host/v1:latest"
        "host:port/v1"   | "name@latest" | "host:port/v1:latest"
        "host:port/v1:1" | "name@latest" | "host:port/v1:latest"
        "v1"             | "name@v2:latest" | "v2:latest"
        "v1:1"           | "name@v2:latest" | "v2:latest"
        "host/v1"        | "name@v2:latest" | "v2:latest"
        "host/v1:1"      | "name@v2:latest" | "v2:latest"
        "host:port/v1"   | "name@v2:latest" | "v2:latest"
        "host:port/v1:1" | "name@v2:latest" | "v2:latest"
        "v1"             | "name@host/v2" | "host/v2"
        "v1:1"           | "name@host/v2" | "host/v2"
        "host/v1"        | "name@host/v2" | "host/v2"
        "host/v1:1"      | "name@host/v2" | "host/v2"
        "host:port/v1"   | "name@host/v2" | "host/v2"
        "host:port/v1:1" | "name@host/v2" | "host/v2"
        "v1"             | "name@host/v2:2" | "host/v2:2"
        "v1:1"           | "name@host/v2:2" | "host/v2:2"
        "host/v1"        | "name@host/v2:2" | "host/v2:2"
        "host/v1:1"      | "name@host/v2:2" | "host/v2:2"
        "host:port/v1"   | "name@host/v2:2" | "host/v2:2"
        "host:port/v1:1" | "name@host/v2:2" | "host/v2:2"
        "v1"             | "name@host:port/v2:2" | "host:port/v2:2"
        "v1:1"           | "name@host:port/v2:2" | "host:port/v2:2"
        "host/v1"        | "name@host:port/v2:2" | "host:port/v2:2"
        "host/v1:1"      | "name@host:port/v2:2" | "host:port/v2:2"
        "host:port/v1"   | "name@host:port/v2:2" | "host:port/v2:2"
        "host:port/v1:1" | "name@host:port/v2:2" | "host:port/v2:2"
    }

    def 'can add entire directories via copyspec'() {
        given:
        String id = 'id1'
        createFile("myDir/bar")
        file('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER ${id}
            ADD myDir /myDir/
        """.stripIndent()
        buildFile << """
            plugins {
                id 'com.palantir.docker'
            }

            docker {
                name '${id}'
                copySpec.from("myDir").into("myDir")
            }
        """.stripIndent()
        when:
        BuildResult buildResult = with('docker').build()

        then:
        buildResult.task(':dockerPrepare').outcome == TaskOutcome.SUCCESS
        file("build/docker/myDir/bar").exists()
    }
}
