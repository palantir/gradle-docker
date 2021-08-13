/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

    private static final String SEPARATOR = System.lineSeparator()

    def 'can run, status, and stop a container made by the docker plugin' () {
        given:
        file('Dockerfile') << '''
            FROM alpine:3.2
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
        buildResult.output =~ /(?m):dockerRun(WARNING:.*${SEPARATOR})?${SEPARATOR}[A-Za-z0-9]+/

        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'foo' is RUNNING./

        buildResult.task(':dockerStop').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerStop${SEPARATOR}foo/

        offline.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        offline.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'foo' is STOPPED./

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
                image 'alpine:3.2'
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
        buildResult.output =~ /(?m):dockerRun(WARNING:.*${SEPARATOR})?${SEPARATOR}[A-Za-z0-9]+/

        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'bar' is RUNNING./

        buildResult.task(':dockerStop').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerStop${SEPARATOR}bar/

        offline.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        offline.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'bar' is STOPPED./
    }

    def 'can run container with configured network' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.docker-run'
            }
            dockerRun {
                name 'bar-hostnetwork'
                image 'alpine:3.2'
                network 'host'
            }
        '''.stripIndent()

		when:
		BuildResult buildResult = with('dockerRemoveContainer', 'dockerRun', 'dockerNetworkModeStatus').build()

		then:
		buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

		buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS

		buildResult.output =~ /(?m):dockerNetworkModeStatus${SEPARATOR}Docker container 'bar-hostnetwork' is configured to run with 'host' network mode./
	}

    def 'can optionally not daemonize'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.docker-run'
            }

            dockerRun {
                name 'bar-nodaemonize'
                image 'alpine:3.2'
                ports '8080'
                command 'echo', '"hello world"'
                daemonize false
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('dockerRemoveContainer', 'dockerRun', 'dockerRunStatus').build()

        then:
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'bar-nodaemonize' is STOPPED./
    }

    def 'can set additional arguments'() {
        given:
        file('Dockerfile') << '''
                FROM alpine:3.2
                 RUN mkdir /test
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
                     arguments '-w=/test'
                     daemonize false
                command 'pwd'
                }
          '''.stripIndent()

        when:
        BuildResult buildResult = with('docker', 'dockerRemoveContainer', 'dockerRun', 'dockerRunStatus').build()

        then:
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m)\/test/

        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus\nDocker container 'foo' is STOPPED./
    }

    def 'can mount volumes'() {
        if (isCi()) {
            // circleci has problems removing volumes:
            // see: https://discuss.circleci.com/t/docker-error-removing-intermediate-container/70/10
            return
        }

        given:
        File testFolder = directory("test")
        file('Dockerfile') << '''
            FROM alpine:3.2

            RUN mkdir /test
            VOLUME /test
            CMD cat /test/testfile
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
                volumes "test": "/test"
                daemonize false
            }
        '''.stripIndent()

        when:
        new File(testFolder, "testfile").text = "HELLO WORLD${SEPARATOR}"
        BuildResult buildResult = with('docker', 'dockerRemoveContainer', 'dockerRun', 'dockerRunStatus').build()

        then:
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m)HELLO WORLD/
        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'foo' is STOPPED./
    }

    def 'can mount volumes specified with an absolute path'() {
        if (isCi()) {
            // circleci has problems removing volumes:
            // see: https://discuss.circleci.com/t/docker-error-removing-intermediate-container/70/10
            return
        }

        given:
        File testFolder = directory("test")
        file('Dockerfile') << '''
            FROM alpine:3.2

            RUN mkdir /test
            VOLUME /test
            CMD cat /test/testfile
        '''.stripIndent()
        buildFile << """
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
                volumes "${escapePath(testFolder.absolutePath)}": "/test"
                daemonize false
            }
        """.stripIndent()

        when:
        new File(testFolder, "testfile").text = "HELLO WORLD${SEPARATOR}"
        BuildResult buildResult = with('docker', 'dockerRemoveContainer', 'dockerRun', 'dockerRunStatus').build()

        then:
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m)HELLO WORLD/
        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'foo' is STOPPED./
    }

    def 'can run with environment variables'() {
        given:
        file('Dockerfile') << '''
            FROM alpine:3.2

            RUN mkdir /test
            VOLUME /test
            ENV MYVAR1 QUUW
            ENV MYVAR2 QUUX
            ENV MYVAR3 QUUY
            ENV MYVAR4 QUUZ
            CMD echo "\$MYVAR1 = \$MYVAR2 = \$MYVAR3 = \$MYVAR4"
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
                name 'foo-envvars'
                image 'foo-image:latest'
                env 'MYVAR1': 'FOO', 'MYVAR2': 'BAR', 'MYVAR4': 'ZIP'
                daemonize false
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('docker', 'dockerRemoveContainer', 'dockerRun', 'dockerRunStatus').build()

        then:
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m)FOO = BAR = QUUY = ZIP/
        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
        buildResult.output =~ /(?m):dockerRunStatus${SEPARATOR}Docker container 'foo-envvars' is STOPPED./
    }


    def isLinux() {
        return System.getProperty("os.name") =~ /(?i).*linux.*/
    }

    def isCi() {
        return System.getenv("CI") == "true"
    }


}
