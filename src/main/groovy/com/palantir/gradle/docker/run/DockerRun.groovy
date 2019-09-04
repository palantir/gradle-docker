package com.palantir.gradle.docker.run

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import com.palantir.gradle.docker.DockerRunPlugin
import org.gradle.api.provider.Property
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory

class DockerRun extends DockerRunBaseTask {

    Property<String> image = project.objects.property(String)
    Property<List<String>> command = project.objects.property(List)
    Property<Set<String>> ports = project.objects.property(Set)
    Property<Map<String, String>> env = project.objects.property(Map)
    Property<Map<Object, String>> volumes = project.objects.property(Map)
    Property<Boolean> daemonize = project.objects.property(Boolean)
    Property<Boolean> clean = project.objects.property(Boolean)

    DockerRun() {
        super(DockerRun.class)
        group = 'Docker Run'
        description = 'Runs the specified container with port mappings'

        image.set(getExtension().image)
        command.set(getExtension().command)
        ports.set(getExtension().ports)
        env.set(getExtension().env)
        volumes.set(getExtension().volumes)
        daemonize.set(getExtension().daemonize)
        clean.set(getExtension().clean)

        def internalName = containerName
        def internalRunStatus = project.tasks.create(getName() + DockerRunStatus.getSimpleName(), DockerRunStatus.class, {
            containerName.set internalName
        })

        project.afterEvaluate {
            List<String> args = Lists.newArrayList()
            args.addAll(['docker', 'run'])
            if (daemonize.getOrElse(true)) {
                args.add('-d')
            }
            if (clean.getOrElse(false)) {
                args.add('--rm')
            } else {
                finalizedBy internalRunStatus
            }
            if (network.isPresent()) {
                args.addAll(['--network', network.get()])
            }
            for (String port : ports.get()) {
                args.add('-p')
                args.add(port)
            }
            for (Map.Entry<Object, String> volume : volumes.get().entrySet()) {
                File localFile = project.file(volume.key)

                if (!localFile.exists()) {
                    StyledTextOutput o = project.services.get(StyledTextOutputFactory.class).create(DockerRunPlugin)
                    o.withStyle(StyledTextOutput.Style.Error).println("ERROR: Local folder ${localFile} doesn't exist. Mounted volume will not be visible to container")
                    throw new IllegalStateException("Local folder ${localFile} doesn't exist.")
                }

                args.add('-v')
                args.add("${localFile.absolutePath}:${volume.value}")
            }
            args.addAll(env.get().collect { k, v -> ['-e', "${k}=${v}"] }.flatten())
            args.addAll(['--name', containerName.getOrNull(), image.getOrNull()])
            if (!command.get().isEmpty()) {
                args.addAll(command.get())
            }
            commandLine args

        }
    }

    def image(String image) {
        this.image.set image
    }

    def env(Map<String, String> env) {
        this.env.set env
    }

    def daemonize(boolean daemonize) {
        this.daemonize.set daemonize
    }

    def clean(boolean clean) {
        this.clean.set clean
    }

    def command(String[] command) {
        this.command.set Arrays.asList(command)
    }

    def command(List<String> command) {
        this.command.set command
    }

    def ports(String... ports) {
        ImmutableSet.Builder builder = ImmutableSet.<String> builder()
        for (String port : ports) {
            String[] mapping = port.split(':', 2)
            if (mapping.length == 1) {
                checkPortIsValid(mapping[0])
                builder.add("${mapping[0]}:${mapping[0]}")
            } else {
                checkPortIsValid(mapping[0])
                checkPortIsValid(mapping[1])
                builder.add("${mapping[0]}:${mapping[1]}")
            }
        }
        this.ports.set builder.build()
    }

    private static void checkPortIsValid(String port) {
        int val = Integer.parseInt(port)
        Preconditions.checkArgument(0 < val && val <= 65536, "Port must be in the range [1,65536]")
    }

    def volumes(Map<Object, String> volumes) {
        this.volumes.set ImmutableMap.copyOf(volumes)
    }
}
