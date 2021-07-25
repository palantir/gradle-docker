package com.palantir.gradle.docker.task.dockerrun

import com.palantir.gradle.docker.DockerRunExtension

import javax.inject.Inject

class DockerNetworkModeStatusTask extends AbstractDockerRunTask {
    @Inject
    DockerNetworkModeStatusTask(DockerRunExtension injectedDockerRunExtension) {
        super(injectedDockerRunExtension)
        description = 'Checks the network configuration of the container'

        standardOutput = new ByteArrayOutputStream()
        commandLine 'docker', 'inspect', '--format={{.HostConfig.NetworkMode}}', dockerRunExtension.name
        doLast {
            def networkMode = standardOutput.toString().trim()
            if (networkMode == 'default') {
                println "Docker container '${dockerRunExtension.name}' has default network configuration (bridge)."
            } else {
                if (networkMode == dockerRunExtension.network) {
                    println "Docker container '${dockerRunExtension.name}' is configured to run with '${dockerRunExtension.network}' network mode."
                } else {
                    println "Docker container '${dockerRunExtension.name}' runs with '${networkMode}' network mode instead of the configured '${dockerRunExtension.network}'."
                    return 1
                }
            }
        }
    }
}
