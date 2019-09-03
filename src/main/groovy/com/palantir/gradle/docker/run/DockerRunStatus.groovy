package com.palantir.gradle.docker.run

class DockerRunStatus extends DockerRunBaseTask {

    DockerRunStatus() {
        super(DockerRunStatus.class)
        group = 'Docker Run'
        description = 'Checks the run status of the container'

        project.afterEvaluate {
            standardOutput = new ByteArrayOutputStream()
            commandLine 'docker', 'inspect', '--format={{.State.Running}}', containerName.getOrNull()
        }

        doLast {
            if (standardOutput.toString().trim() != 'true') {
                println "Docker container '${containerName.getOrNull()}' is STOPPED."
                return 1
            } else {
                println "Docker container '${containerName.getOrNull()}' is RUNNING."
            }
        }
    }
}
