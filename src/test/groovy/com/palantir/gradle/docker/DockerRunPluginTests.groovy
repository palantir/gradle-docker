/*
 * Copyright 2016 Palantir Technologies
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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome


class DockerRunPluginTests extends AbstractPluginTest {

    def 'can run, status, and stop a container made by the docker plugin' () {
        given:
        temporaryFolder.newFile('Dockerfile') << '''
            FROM alpine:edge
            CMD sleep 1000
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker'
                id 'com.palantir.docker-run'
            }

            docker {
                name 'foo-image:latest'
            }

            dockerRun {
                name 'foo'
                image 'foo-image:latest'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('docker', 'dockerRemoveContainer', 'dockerRun', 'dockerRunStatus', 'dockerStop').build()
        BuildResult offline = with('dockerRunStatus', 'dockerRemoveContainer').build()

        then:
        buildResult.task(':docker').outcome == TaskOutcome.SUCCESS
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS
        // CircleCI build nodes print a WARNING
        buildResult.output =~ /(?m):dockerRun(WARNING:.*\n)?\n[A-Za-z0-9]+/

        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus\nDocker container 'foo' is RUNNING./

        buildResult.task(':dockerStop').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerStop\nfoo/

        offline.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        offline.output =~ /(?m):dockerRunStatus\nDocker container 'foo' is STOPPED./

        execCond('docker rmi -f foo-image')
    }

    def 'can run, status, and stop a container' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.docker-run'
            }

            dockerRun {
                name 'bar'
                image 'alpine:edge'
                ports '8080'
                command 'sleep', '1000'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('dockerRemoveContainer', 'dockerRun', 'dockerRunStatus', 'dockerStop').build()
        BuildResult offline = with('dockerRunStatus', 'dockerRemoveContainer').build()

        then:
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS
        // CircleCI build nodes print a WARNING
        buildResult.output =~ /(?m):dockerRun(WARNING:.*\n)?\n[A-Za-z0-9]+/

        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus\nDocker container 'bar' is RUNNING./

        buildResult.task(':dockerStop').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerStop\nbar/

        offline.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        offline.output =~ /(?m):dockerRunStatus\nDocker container 'bar' is STOPPED./
    }

}
