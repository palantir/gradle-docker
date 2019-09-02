package com.palantir.gradle.docker.run

class DockerNetworkModeStatus extends DockerRunBaseTask {

    String network

    DockerNetworkModeStatus() {
        super(DockerNetworkModeStatus.class)
        group = 'Docker Run'
        description = 'Checks the network configuration of the container'

        project.afterEvaluate {
            standardOutput = new ByteArrayOutputStream()
            commandLine 'docker', 'inspect', '--format={{.HostConfig.NetworkMode}}', containerName
        }

        doLast {
            def networkMode = standardOutput.toString().trim()
            if (networkMode == 'default') {
                println "Docker container '${containerName}' has default network configuration (bridge)."
            } else {
                if (networkMode == network) {
                    println "Docker container '${containerName}' is configured to run with '${network}' network mode."
                } else {
                    println "Docker container '${containerName}' runs with '${networkMode}' network mode instead of the configured '${network}'."
                    return 1
                }
            }
        }

    }

    def network(String network) {
        this.network = network
    }
}
