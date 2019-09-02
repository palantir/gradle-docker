package com.palantir.gradle.docker.run

import org.gradle.api.tasks.AbstractExecTask

abstract class DockerRunBaseTask extends AbstractExecTask {
    String containerName

    DockerRunBaseTask(Class taskType) {
        super(taskType)
    }

    def name(String name) {
        this.containerName = name
    }

    def containerName(String containerName) {
        this.containerName = containerName
    }
}
