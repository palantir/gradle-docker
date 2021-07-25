/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.docker

import static com.google.common.base.Preconditions.checkNotNull

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.palantir.gradle.docker.task.dockerrun.*
import org.gradle.api.Project

class DockerRunExtension {
    private String name
    private String image
    private String network
    private List<String> command = ImmutableList.of()
    private Set<String> ports = ImmutableSet.of()
    private Map<String, String> env = ImmutableMap.of()
    private List<String> arguments = ImmutableList.of()
    private Map<Object, String> volumes = ImmutableMap.of()
    private boolean daemonize = true
    private boolean clean = false
    static final String DEFAULT_EXTENSION_NAME = "dockerRun"

    DockerRunExtension(String extensionName, Project project) {
        String taskNamePrefix = 'docker'
        if (DEFAULT_EXTENSION_NAME != extensionName) {
            taskNamePrefix = extensionName
        }
        project.tasks.register("${taskNamePrefix}Run", DockerRunTask, this)
        project.tasks.register("${taskNamePrefix}Stop", DockerStopTask, this)
        project.tasks.register("${taskNamePrefix}RemoveContainer", DockerRemoveTask, this)
        project.tasks.register("${taskNamePrefix}RunStatus", DockerRunStatusTask, this)
        project.tasks.register("${taskNamePrefix}NetworkModeStatus", DockerNetworkModeStatusTask, this)
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    boolean getDaemonize() {
        return daemonize
    }

    void setDaemonize(boolean daemonize) {
        this.daemonize = daemonize
    }

    boolean getClean() {
        return clean
    }

    void setClean(boolean clean) {
        this.clean = clean
    }

    String getImage() {
        return image
    }

    void setImage(String image) {
        this.image = image
    }

    Set<String> getPorts() {
        return ports
    }

    List<String> getCommand() {
        return command
    }

    Map<Object, String> getVolumes() {
        return volumes
    }

    void command(String... command) {
        this.command = ImmutableList.copyOf(command)
    }

    void setNetwork(String network) {
        this.network = network
    }

    String getNetwork() {
        return network
    }

    private void setEnvSingle(String key, String value) {
        this.env.put(checkNotNull(key, "key"), checkNotNull(value, "value"))
    }

    void env(Map<String, String> env) {
        this.env = ImmutableMap.copyOf(env)
    }

    Map<String, String> getEnv() {
        return env
    }

    void arguments(String... arguments) {
        this.arguments = ImmutableList.copyOf(arguments)
    }

    List<String> getArguments() {
        return arguments
    }

    void ports(String... ports) {
        ImmutableSet.Builder builder = ImmutableSet.builder()
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

    void volumes(Map<Object, String> volumes) {
        this.volumes = ImmutableMap.copyOf(volumes)
    }

    private static void checkPortIsValid(String port) {
        int val = Integer.parseInt(port)
        Preconditions.checkArgument(0 < val && val <= 65536, "Port must be in the range [1,65536]")
    }

}
