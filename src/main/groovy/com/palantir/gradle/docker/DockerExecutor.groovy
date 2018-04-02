package com.palantir.gradle.docker

import com.google.common.annotations.VisibleForTesting

/**
 * A trait for classes which might execute the Docker process. Provides a mechanism for them to retrieve the path to the
 * Docker binary via {@link #getDockerBinary}
 */
trait DockerExecutor {
    private static final String DEFAULT_DOCKER_BINARY = "docker"

    /**
     * @return the path to the Docker binary. If the <pre>GRADLE_DOCKER_BINARY</pre> environment variable is specified,
     * it will be returned, otherwise defaults to {@link #DEFAULT_DOCKER_BINARY}. No guarantees are made about the
     * existence or executability of the returned file.
     */
    String getDockerBinary() {
        return getEnvironmentVariable('GRADLE_DOCKER_BINARY') ?: DEFAULT_DOCKER_BINARY
    }

    /**
     * Gets environment variables based on the given parameter. {@link VisibleForTesting} because there's no real world
     * reason to override this, but modifying the environment is not trivial in testing.
     *
     * @param environment variable name
     * @return the environment variable value for the given name.
     */
    @VisibleForTesting
    String getEnvironmentVariable(String name) {
        return System.getenv(name)
    }
}