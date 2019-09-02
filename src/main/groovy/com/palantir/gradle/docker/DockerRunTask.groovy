package com.palantir.gradle.docker

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory

class DockerRunTask extends AbstractExecTask {

    String containerName
    String image
    String network
    List<String> command = ImmutableList.of()
    Set<String> ports = ImmutableSet.of()
    Map<String, String> env = ImmutableMap.of()
    Map<Object, String> volumes = ImmutableMap.of()
    boolean daemonize = true
    boolean clean = false

    DockerRunTask() {
        super(DockerRunTask.class)
        group = 'Docker Run'
        description = 'Runs the specified container with port mappings'
        project.afterEvaluate {
            List<String> args = Lists.newArrayList()
            args.addAll(['docker', 'run'])
            if (daemonize) {
                args.add('-d')
            }
            if (clean) {
                args.add('--rm')
            } else {
                finalizedBy project.tasks.dockerRunStatus
            }
            if (network) {
                args.addAll(['--network', network])
            }
            for (String port : ports) {
                args.add('-p')
                args.add(port)
            }
            for (Map.Entry<Object, String> volume : volumes.entrySet()) {
                File localFile = project.file(volume.key)

                if (!localFile.exists()) {
                    StyledTextOutput o = project.services.get(StyledTextOutputFactory.class).create(DockerRunPlugin)
                    o.withStyle(StyledTextOutput.Style.Error).println("ERROR: Local folder ${localFile} doesn't exist. Mounted volume will not be visible to container")
                    throw new IllegalStateException("Local folder ${localFile} doesn't exist.")
                }

                args.add('-v')
                args.add("${localFile.absolutePath}:${volume.value}")
            }
            args.addAll(env.collect { k, v -> ['-e', "${k}=${v}"] }.flatten())
            args.addAll(['--name', containerName, image])
            if (!command.isEmpty()) {
                args.addAll(command)
            }
            assert !args.contains(null)
            commandLine args
        }
    }

    def name(String name) {
        this.containerName = name
    }

    def containerName(String containerName) {
        this.containerName = containerName
    }

    def image(String image) {
        this.image = image
    }

    def network(String network) {
        this.network = network
    }

    def env(Map<String, String> env) {
        this.env = env
    }

    def daemonize(boolean daemonize) {
        this.daemonize = daemonize
    }

    def clean(boolean clean) {
        this.clean = clean
    }

    def command(String[] command) {
        this.command = Arrays.asList(command)
    }

    def command(List<String> command) {
        this.command = command
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
        this.ports = builder.build()
    }

    private static void checkPortIsValid(String port) {
        int val = Integer.parseInt(port)
        Preconditions.checkArgument(0 < val && val <= 65536, "Port must be in the range [1,65536]")
    }

    def volumes(Map<Object, String> volumes) {
        this.volumes = ImmutableMap.copyOf(volumes)
    }
}
