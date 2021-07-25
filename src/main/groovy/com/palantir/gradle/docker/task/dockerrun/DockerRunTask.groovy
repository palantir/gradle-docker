package com.palantir.gradle.docker.task.dockerrun

import com.google.common.collect.Lists
import com.palantir.gradle.docker.DockerRunExtension
import org.gradle.internal.logging.text.StyledTextOutput

import javax.inject.Inject

class DockerRunTask extends AbstractDockerRunTask {

    @Inject
    DockerRunTask(DockerRunExtension injectedDockerRunExtension) {
        super(injectedDockerRunExtension)
        description = 'Runs the specified container with port mappings'

        List<String> args = Lists.newArrayList()
        args.addAll(['docker', 'run'])
        if (dockerRunExtension.daemonize) {
            args.add('-d')
        }
        if (dockerRunExtension.clean) {
            args.add('--rm')
        }
        // TODO dynamic runStatus task
        else {
            finalizedBy project.tasks.dockerRunStatus
        }
        if (dockerRunExtension.network) {
            args.addAll(['--network', dockerRunExtension.network])
        }
        for (String port : dockerRunExtension.ports) {
            args.add('-p')
            args.add(port)
        }
        for (Map.Entry<Object, String> volume : dockerRunExtension.volumes.entrySet()) {
            File localFile = project.file(volume.key)

            if (!localFile.exists()) {
                StyledTextOutput o = project.services.get(StyledTextOutputFactory.class).create(DockerRunTask)
                o.withStyle(StyledTextOutput.Style.Error).
                println("ERROR: Local folder ${localFile} doesn't exist. Mounted volume will not be visible to container")
                throw new IllegalStateException("Local folder ${localFile} doesn't exist.")
            }

            args.add('-v')
            args.add("${localFile.absolutePath}:${volume.value}")
        }
        args.addAll(dockerRunExtension.env.collect { k, v -> ['-e', "${k}=${v}"] }.flatten())
        args.add('--name')
        args.add(dockerRunExtension.name)
        if (!dockerRunExtension.arguments.isEmpty()) {
            args.addAll(dockerRunExtension.arguments)
        }
        args.add(dockerRunExtension.image)
        if (!dockerRunExtension.command.isEmpty()) {
            args.addAll(dockerRunExtension.command)
        }
        commandLine args
    }

}
