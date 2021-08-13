/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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

class DockerComposePluginTests extends AbstractPluginTest {

    def 'Generates docker-compose.yml from template with version strings replaced'() {
        given:
        file('Dockerfile') << "Foo"
        file("docker-compose.yml.template") << '''
            service1:
              image: 'repository/service1:{{com.google.guava:guava}}'
            service2:
              image: 'repository/service2:{{org.slf4j:slf4j-api}}'
            current-service:
              image: '{{currentImageName}}'
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }

            repositories {
                jcenter()
            }

            dockerCompose {
                templateTokens(['currentImageName': 'snapshot.docker.registry/current-service:1.0.0-1-gabcabcd'])
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
        def dockerComposeText = file("docker-compose.yml").text
        dockerComposeText.contains("repository/service1:18.0")
        dockerComposeText.contains("repository/service2:1.7.10")
        dockerComposeText.contains("image: 'snapshot.docker.registry/current-service:1.0.0-1-gabcabcd'")
    }

    def 'Fails if docker-compose.yml.template has unmatched version tokens'() {
        given:
        file('Dockerfile') << "Foo"
        file("docker-compose.yml.template") << '''
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
        file('Dockerfile') << "Foo"
        file("templates/customTemplate.yml") << '''
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
        file("compose-files/customDockerCompose.yml").exists()
    }

    def 'Fails if template is configured but does not exist'() {
        given:
        file('Dockerfile') << "Foo"
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

    def 'docker-compose is executed and fails on invalid file'() {
        given:
        file('docker-compose.yml') << "FOO"
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }
        '''.stripIndent()
        when:
        BuildResult buildResult = with('dockerComposeUp', "--stacktrace").buildAndFail()
        then:
        buildResult.output.contains("Top level")
    }

    def 'docker-compose successfully creates docker image'() {
        given:
        file('docker-compose.yml') << '''
            version: "2"
            services:
              hello:
                container_name: "helloworld"
                image: "alpine"
                command: touch /test/foobarbaz
                volumes:
                  - ./:/test
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }
        '''.stripIndent()
        when:
        with('dockerComposeUp').build()
        then:
        file("foobarbaz").exists()
    }

    def 'docker-compose successfully creates docker image from custom file'() {
        given:
        file('test-file.yml') << '''
            version: "2"
            services:
              hello:
                container_name: "helloworld2"
                image: "alpine"
                command: touch /test/qux
                volumes:
                  - ./:/test
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }

            dockerCompose {
              dockerComposeFile "test-file.yml"
            }
        '''.stripIndent()
        when:
        with('dockerComposeUp').build()
        then:
        file("qux").exists()
    }

    def 'can set custom properties on generateDockerCompose.ext'() {
        given:
        file('docker-compose.yml.template') << '''
            version: "2"
            services: {}
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }

            generateDockerCompose.ext.foo = "bar"
        '''.stripIndent()
        when:
        BuildResult buildResult = with('generateDockerCompose').build()
        then:
        buildResult.task(':generateDockerCompose').outcome == TaskOutcome.SUCCESS
    }

    def 'docker-compose stop successfully stops docker container'() {
        given:
        file('docker-compose.yml') << '''
            version: "2"
            services:
              hello:
                container_name: "unit-test-docker-compose-stop"
                image: "alpine"
                command: sh -c 'while sleep 3600; do :; done\'
        '''.stripIndent()
        buildFile << '''
            plugins {
                id 'com.palantir.docker-compose'
            }
        '''.stripIndent()
        with('dockerComposeUp').build()
        when:
        with('dockerComposeDown').build()
        then:
        processCount() == 0
    }
}
