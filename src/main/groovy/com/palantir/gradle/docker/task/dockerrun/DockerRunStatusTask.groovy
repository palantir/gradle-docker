package com.palantir.gradle.docker.task.dockerrun

import com.palantir.gradle.docker.DockerRunExtension

import javax.inject.Inject

class DockerRunStatusTask extends AbstractDockerRunTask {
    @Inject
    DockerRunStatusTask(DockerRunExtension injectedDockerRunExtension) {
        super(injectedDockerRunExtension)
        description = 'Checks the run status of the container'

        standardOutput = new ByteArrayOutputStream()
        commandLine 'docker', 'inspect', '--format={{.State.Running}}', dockerRunExtension.name
        doLast {
            if (standardOutput.toString().trim() != 'true') {
                println "Docker container '${dockerRunExtension.name}' is STOPPED."
                return 1
            } else {
                println "Docker container '${dockerRunExtension.name}' is RUNNING."
            }
        }
    }
}
