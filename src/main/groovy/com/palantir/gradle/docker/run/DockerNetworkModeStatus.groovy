package com.palantir.gradle.docker.run

class DockerNetworkModeStatus extends DockerRunBaseTask {

    DockerNetworkModeStatus() {
        super(DockerNetworkModeStatus.class)
        group = 'Docker Run'
        description = 'Checks the network configuration of the container'

        project.afterEvaluate {
            standardOutput = new ByteArrayOutputStream()
            commandLine 'docker', 'inspect', '--format={{.HostConfig.NetworkMode}}', containerName.getOrNull()
        }

        doLast {
            def networkMode = standardOutput.toString().trim()
            if (networkMode == 'default') {
                println "Docker container '${containerName.getOrNull()}' has default network configuration (bridge)."
            } else {
                if (networkMode == network.get()) {
                    println "Docker container '${containerName.getOrNull()}' is configured to run with '${network.getOrNull()}' network mode."
                } else {
                    println "Docker container '${containerName.getOrNull()}' runs with '${networkMode}' network mode instead of the configured '${network.getOrNull()}'."
                    return 1
                }
            }
        }

    }
}
