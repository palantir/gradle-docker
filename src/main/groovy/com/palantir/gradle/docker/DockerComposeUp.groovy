package com.palantir.gradle.docker

import groovy.util.logging.Slf4j
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

@Slf4j
class DockerComposeUp extends DefaultTask {
    DockerComposeExtension ext
    Configuration configuration

    DockerComposeUp() {
        this.group = 'Docker'
    }

    @TaskAction
    void run() {
        project.exec {
            it.executable "docker-compose"
            it.args "-f", getDockerComposeFile(), "up", "-d"
        }
    }

    @Override
    String getDescription() {
        def defaultDescription = "Executes `docker-compose` using ${ext.dockerComposeFile.name}"
        return super.description ?: defaultDescription
    }

    @InputFiles
    File getDockerComposeFile() {
        return ext.dockerComposeFile
    }
}
