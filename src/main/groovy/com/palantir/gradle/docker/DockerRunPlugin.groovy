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

import java.util.Map.Entry

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.StyledTextOutput.Style

import com.google.common.collect.Lists

class DockerRunPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerRunExtension ext = project.extensions.create('dockerRun', DockerRunExtension)

        Exec dockerRunStatus = project.tasks.create('dockerRunStatus', Exec, {
            group = 'Docker Run'
            description = 'Checks the run status of the container'
        })

        Exec dockerRun = project.tasks.create('dockerRun', Exec, {
            group = 'Docker Run'
            description = 'Runs the specified container with port mappings'
        })

        Exec dockerStop = project.tasks.create('dockerStop', Exec, {
            group = 'Docker Run'
            description = 'Stops the named container if it is running'
            ignoreExitValue = true
        })

        Exec dockerRemoveContainer = project.tasks.create('dockerRemoveContainer', Exec, {
            group = 'Docker Run'
            description = 'Removes the persistent container associated with the Docker Run tasks'
            ignoreExitValue = true
        })

        Exec dockerNetworkModeStatus = project.tasks.create('dockerNetworkModeStatus', Exec, {
            group = 'Docker Run'
            description = 'Checks the network configuration of the container'
        })

        project.afterEvaluate {
            dockerRunStatus.with {
                standardOutput = new ByteArrayOutputStream()
                commandLine 'docker', 'inspect', '--format={{.State.Running}}', ext.name
                doLast {
                    if (standardOutput.toString().trim() != 'true') {
                        println "Docker container '${ext.name}' is STOPPED."
                        return 1
                    } else {
                        println "Docker container '${ext.name}' is RUNNING."
                    }
                }
            }

            dockerNetworkModeStatus.with {
                standardOutput = new ByteArrayOutputStream()
                commandLine 'docker', 'inspect', '--format={{.HostConfig.NetworkMode}}', ext.name
                doLast {
                    def networkMode = standardOutput.toString().trim()
                    if (networkMode == 'default') {
                        println "Docker container '${ext.name}' has default network configuration (bridge)."
                    }
                    else {
                        if (networkMode == ext.network) {
                            println "Docker container '${ext.name}' is configured to run with '${ext.network}' network mode."
                        }
                        else {
                            println "Docker container '${ext.name}' runs with '${networkMode}' network mode instead of the configured '${ext.network}'."
                            return 1
                        }
                    }
                }
            }

            dockerRun.with {
                List<String> args = Lists.newArrayList()
                args.addAll(['docker', 'run'])
                if (ext.daemonize) {
                    args.add('-d')
                }
                if (ext.clean) {
                    args.add('--rm')
                } else {
                    finalizedBy dockerRunStatus
                }
                if (ext.network) {
                    args.addAll(['--network', ext.network])
                }
                for (String port : ext.ports) {
                    args.add('-p')
                    args.add(port)
                }
                for (Entry<String, String> volume : ext.volumes.entrySet()) {
                    File localFile = new File(project.projectDir, volume.key)

                    if (!localFile.exists()) {
                        StyledTextOutput o = project.services.get(StyledTextOutputFactory.class).create(DockerRunPlugin)
                        o.withStyle(Style.Error).println("ERROR: Local folder ${localFile} doesn't exist. Mounted volume will not be visible to container")
                        throw new IllegalStateException("Local folder ${localFile} doesn't exist.")
                    }

                    args.add('-v')
                    args.add("${localFile.absolutePath}:${volume.value}")
                }

                args.addAll(ext.env.collect { k, v -> ['-e', "${k}=${v}"] }.flatten())

                args.addAll(ext.hosts.collect { k, v -> ['--add-host', "${k}:${v}"] }.flatten())

                if (ext.link) {
                    args.add('--link')
                    args.add(ext.link)
                }
                args.addAll(['--name', ext.name, ext.image])
                if (!ext.command.isEmpty()) {
                    args.addAll(ext.command)
                }
                commandLine args
            }

            dockerStop.with {
                commandLine 'docker', 'stop', ext.name
            }

            dockerRemoveContainer.with {
                commandLine 'docker', 'rm', ext.name
            }
        }
    }
}
