package com.palantir.gradle.docker.run

class DockerRemoveContainer extends DockerRunBaseTask {

    DockerRemoveContainer() {
        super(DockerRemoveContainer.class)
        group = 'Docker Run'
        description = 'Removes the persistent container associated with the Docker Run tasks'
        ignoreExitValue = true
        project.afterEvaluate {
            commandLine 'docker', 'rm', containerName.get()
        }
    }

}
