package com.palantir.gradle.docker.task.dockerrun

import com.palantir.gradle.docker.DockerRunExtension

import javax.inject.Inject

class DockerRemoveTask extends AbstractDockerRunTask {

    @Inject
    DockerRemoveTask(DockerRunExtension injectedDockerRunExtension) {
        super(injectedDockerRunExtension)
        description = 'Removes the persistent container associated with the Docker Run tasks'
        ignoreExitValue = true
        commandLine 'docker', 'rm', dockerRunExtension.name
    }
}
