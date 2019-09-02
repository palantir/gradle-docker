/*
 * Copyright 2016 Palantir Technologies
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

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.provider.Property

class DockerRunExtension {

    Property<String> name
    Property<String> image
    Property<String> network
    Property<List<String>> command
    Property<Set<String>> ports
    Property<Map<String, String>> env
    Property<Map<Object, String>> volumes
    Property<Boolean> daemonize
    Property<Boolean> clean

    DockerRunExtension(Project project) {
        name = project.objects.property(String)
        image = project.objects.property(String)
        network = project.objects.property(String)
        command = project.objects.property(List)
        ports = project.objects.property(Set)
        env = project.objects.property(Map)
        volumes = project.objects.property(Map)
        daemonize = project.objects.property(Boolean)
        clean = project.objects.property(Boolean)

        command.set(ImmutableList.of())
        ports.set(ImmutableSet.of())
        env.set(ImmutableMap.of())
        volumes.set(ImmutableMap.of())

        daemonize.set(true)
        clean.set(false)
    }

    public void setName(String name) {
        this.name.set name
    }

    public void setDaemonize(boolean daemonize) {
        this.daemonize.set daemonize
    }

    public void setClean(boolean clean) {
        this.clean.set clean
    }

    public void setImage(String image) {
        this.image.set image
    }

    public void command(String... command) {
        this.command.set ImmutableList.copyOf(command)
    }

    public void setNetwork(String network) {
        this.network.set network
    }

    public void env(Map<String, String> env) {
        this.env.set ImmutableMap.copyOf(env)
    }

    public void ports(String... ports) {
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
        this.ports.set builder.build()
    }

    public void volumes(Map<Object, String> volumes) {
        this.volumes.set ImmutableMap.copyOf(volumes)
    }

    private static void checkPortIsValid(String port) {
        int val = Integer.parseInt(port)
        Preconditions.checkArgument(0 < val && val <= 65536, "Port must be in the range [1,65536]")
    }

}
