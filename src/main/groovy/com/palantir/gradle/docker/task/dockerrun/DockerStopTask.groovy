package com.palantir.gradle.docker.task.dockerrun

import com.palantir.gradle.docker.DockerRunExtension

import javax.inject.Inject

class DockerStopTask extends AbstractDockerRunTask {

    @Inject
    DockerStopTask(DockerRunExtension injectedDockerRunExtension) {
        super(injectedDockerRunExtension)
        description = 'Stops the named container if it is running'
        ignoreExitValue = true
        commandLine 'docker', 'stop', dockerRunExtension.name
    }
}
