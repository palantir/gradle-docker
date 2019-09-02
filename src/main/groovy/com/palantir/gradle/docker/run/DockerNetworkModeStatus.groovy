package com.palantir.gradle.docker.run

class DockerNetworkModeStatus extends DockerRunBaseTask {

    DockerNetworkModeStatus() {
        super(DockerNetworkModeStatus.class)
        group = 'Docker Run'
        description = 'Checks the network configuration of the container'

        project.afterEvaluate {
            standardOutput = new ByteArrayOutputStream()
            commandLine 'docker', 'inspect', '--format={{.HostConfig.NetworkMode}}', containerName.get()
        }

        doLast {
            def networkMode = standardOutput.toString().trim()
            if (networkMode == 'default') {
                println "Docker container '${containerName.get()}' has default network configuration (bridge)."
            } else {
                if (networkMode == network.get()) {
                    println "Docker container '${containerName.get()}' is configured to run with '${network.get()}' network mode."
                } else {
                    println "Docker container '${containerName.get()}' runs with '${networkMode}' network mode instead of the configured '${network.get()}'."
                    return 1
                }
            }
        }

    }
}
