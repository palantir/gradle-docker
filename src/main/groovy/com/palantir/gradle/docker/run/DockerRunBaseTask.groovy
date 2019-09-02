package com.palantir.gradle.docker.run

import com.palantir.gradle.docker.DockerRunExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractExecTask

abstract class DockerRunBaseTask extends AbstractExecTask {
    Property<String> containerName = project.objects.property(String)
    Provider<String> network = project.objects.property(String)

    DockerRunBaseTask(Class taskType) {
        super(taskType)
        containerName.set(getExtension().name)
        network.set(getExtension().network)
    }

    protected DockerRunExtension getExtension() {
        project.getExtensions().getByType(DockerRunExtension.class)
    }

    def name(String name) {
        this.containerName.set name
    }

    def containerName(String containerName) {
        this.containerName.set containerName
    }

    def network(String network) {
        this.network.set network
    }
}
