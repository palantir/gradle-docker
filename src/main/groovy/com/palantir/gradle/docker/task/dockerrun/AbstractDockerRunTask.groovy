package com.palantir.gradle.docker.task.dockerrun

import com.palantir.gradle.docker.DockerRunExtension
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input

abstract class AbstractDockerRunTask extends Exec {
    @Input
    DockerRunExtension dockerRunExtension

    AbstractDockerRunTask(DockerRunExtension injectedDockerRunExtension) {
        super()
        group = "Docker Run"

        dockerRunExtension = injectedDockerRunExtension
    }
}
