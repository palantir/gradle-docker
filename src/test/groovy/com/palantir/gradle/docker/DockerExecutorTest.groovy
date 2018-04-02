package com.palantir.gradle.docker

import spock.lang.Specification

class DockerExecutorTest extends Specification {
    def 'defaults to docker'() {
        given:
        def executor = [].withTraits(DockerExecutor)

        when:
        String dockerBinary = executor.dockerBinary

        then:
        dockerBinary == 'docker'
    }

    def 'uses environment variable overrides'() {
        given:
        def executor = new EnvironmentSpecifyingExecutor()

        when:
        String dockerBinary = executor.dockerBinary

        then:
        dockerBinary == '/the/docker/binary'
    }

    private static class EnvironmentSpecifyingExecutor implements DockerExecutor {
        @Override
        String getEnvironmentVariable(String name) {
            return name == 'GRADLE_DOCKER_BINARY' ? '/the/docker/binary' : null
        }
    }
}
