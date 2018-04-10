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

import static com.google.common.base.Preconditions.checkNotNull

class DockerRunExtension {

    private String name
    private String image
    private String network
    private List<String> command = ImmutableList.of()
    private Set<String> ports = ImmutableSet.of()
    private Map<String,String> env = ImmutableMap.of()
    private Map<Object,String> volumes = ImmutableMap.of()
    private boolean daemonize = true
    private boolean clean = false

    public String getName() {
        return name
    }

    public void setName(String name) {
        this.name = name
    }

    public boolean getDaemonize() {
        return daemonize
    }

    public void setDaemonize(boolean daemonize) {
        this.daemonize = daemonize
    }

    public boolean getClean() {
        return clean
    }

    public void setClean(boolean clean) {
        this.clean = clean
    }

    public String getImage() {
        return image
    }

    public void setImage(String image) {
        this.image = image
    }

    public Set<String> getPorts() {
        return ports
    }

    public List<String> getCommand() {
        return command
    }

    public Map<Object,String> getVolumes() {
        return volumes
    }

    public void command(String... command) {
        this.command = ImmutableList.copyOf(command)
    }

    public void setNetwork(String network) {
        this.network = network
    }

    public String getNetwork() {
        return network
    }

    private void setEnvSingle(String key, String value) {
        this.env.put(checkNotNull(key, "key"), checkNotNull(value, "value"))
    }

    public void env(Map<String,String> env) {
        this.env = ImmutableMap.copyOf(env)
    }

    public Map<String, String> getEnv() {
        return env
    }

    public void ports(String... ports) {
        ImmutableSet.Builder builder = ImmutableSet.builder()
        for (String port : ports) {
            String[] mapping = port.split(':', 2)
            if (mapping.length == 1) {
                checkContainerPortIsValid(mapping[0])
                builder.add("${mapping[0]}:${mapping[0]}")
            } else {
                checkHostPortIsValid(mapping[0])
                checkContainerPortIsValid(mapping[1])
                builder.add("${mapping[0]}:${mapping[1]}")
            }
        }
        this.ports = builder.build()
    }

    public void volumes(Map<Object,String> volumes) {
      this.volumes = ImmutableMap.copyOf(volumes)
    }

    private static void checkHostPortIsValid(String port) {
        int val = Integer.parseInt(port)
        Preconditions.checkArgument(0 <= val && val <= 65536, "Host port must be in the range [0,65536]")
    }

    private static void checkContainerPortIsValid(String port) {
        int val = Integer.parseInt(port)
        Preconditions.checkArgument(0 < val && val <= 65536, "Container port must be in the range [1,65536]")
    }
}
