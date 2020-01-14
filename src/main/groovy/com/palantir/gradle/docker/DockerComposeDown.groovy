package com.palantir.gradle.docker

import groovy.util.logging.Slf4j
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

@Slf4j
class DockerComposeDown extends DefaultTask {
    Configuration configuration

    DockerComposeDown() {
        this.group = 'Docker'
    }

    @TaskAction
    void run() {
        project.exec {
            it.executable "docker-compose"
            it.args "-f", getDockerComposeFile(), "down"
        }
    }

    @InputFiles
    File getDockerComposeFile() {
        return dockerComposeExtension.dockerComposeFile
    }

    DockerComposeExtension getDockerComposeExtension() {
        return project.extensions.findByType(DockerComposeExtension)
    }
}
