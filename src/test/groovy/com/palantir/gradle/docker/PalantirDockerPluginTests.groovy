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

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile('build.gradle')

        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        def pluginClasspath = pluginClasspathResource.readLines()
            .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
            .collect { "'$it'" }
            .join(", ")

        buildFile << """
            buildscript {
                dependencies {
                    classpath files($pluginClasspath)
                }
            }
        """.stripIndent()
    }

    def 'fail when missing docker configuration' () {
        given:
        buildFile << '''
            apply plugin: 'com.palantir.docker'
        '''.stripIndent()

        when:
        BuildResult buildResult = with('docker').buildAndFail()

        then:
        buildResult.standardError.contains("name is a required docker configuration item.")
    }

    def 'fail with empty container name' () {
        given:
        buildFile << '''
            apply plugin: 'com.palantir.docker'
            docker {
                name ''
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('docker').buildAndFail()

        then:
        buildResult.standardError.contains("name is a required docker configuration item.")
    }

    def 'fail with missing dockerfile' () {
        given:
        buildFile << '''
            apply plugin: 'com.palantir.docker'
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

    def 'check plugin creates a docker container with default configuration' () {
        given:
        String id = UUID.randomUUID().toString()
        temporaryFolder.newFile('Dockerfile') << """
            FROM alpine:3.2
            MAINTAINER ${id}
        """.stripIndent()
        buildFile << """
            apply plugin: 'com.palantir.docker'

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

    def 'check plugin creates a docker container with non-standard Dockerfile name' () {
        given:
        String id = UUID.randomUUID().toString()
        temporaryFolder.newFile('foo') << """
            FROM alpine:3.2
            MAINTAINER ${id}
        """.stripIndent()
        buildFile << """
            apply plugin: 'com.palantir.docker'

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

    private GradleRunner with(String... tasks) {
        GradleRunner.create().withProjectDir(projectDir).withArguments(tasks)
    }

    private String exec(String task) {
        StringBuffer sout = new StringBuffer(), serr = new StringBuffer()
        Process proc = task.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()
        return sout.toString()
    }

}
