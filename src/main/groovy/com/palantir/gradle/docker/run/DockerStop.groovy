package com.palantir.gradle.docker.run

class DockerStop extends DockerRunBaseTask {

    DockerStop() {
        super(DockerStop.class)
        group = 'Docker Run'
        description = 'Stops the named container if it is running'
        ignoreExitValue = true
        project.afterEvaluate {
            commandLine 'docker', 'stop', containerName.get()
        }
    }
}
