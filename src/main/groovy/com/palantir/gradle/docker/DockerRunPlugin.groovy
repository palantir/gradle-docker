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

import com.google.common.collect.Lists
import java.util.Map.Entry
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput.Style
import org.gradle.internal.logging.text.StyledTextOutputFactory

class DockerRunPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerRunExtension ext = project.extensions.create('dockerRun', DockerRunExtension)

        def dockerRunStatus = project.tasks.register('dockerRunStatus', Exec, {
            group = 'Docker Run'
            description = 'Checks the run status of the container'
        })

        def dockerRun = project.tasks.register('dockerRun', Exec, {
            group = 'Docker Run'
            description = 'Runs the specified container with port mappings'
        })

        def dockerStop = project.tasks.register('dockerStop', Exec, {
            group = 'Docker Run'
            description = 'Stops the named container if it is running'
            ignoreExitValue = true
        })

        def dockerRemoveContainer = project.tasks.register('dockerRemoveContainer', Exec, {
            group = 'Docker Run'
            description = 'Removes the persistent container associated with the Docker Run tasks'
            ignoreExitValue = true
        })

        def dockerNetworkModeStatus = project.tasks.register('dockerNetworkModeStatus', Exec, {
            group = 'Docker Run'
            description = 'Checks the network configuration of the container'
        })

        project.afterEvaluate {
            dockerRunStatus.configure {
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

            dockerNetworkModeStatus.configure {
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

            dockerRun.configure {
                List<String> args = Lists.newArrayList()
                args.addAll(['docker', 'run'])
                ignoreExitValue = ext.ignoreExitValue
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
                for (Entry<Object,String> volume : ext.volumes.entrySet()) {
                    File localFile = project.file(volume.key)

                    if (!localFile.exists()) {
                       StyledTextOutput o = project.services.get(StyledTextOutputFactory.class).create(DockerRunPlugin)
                       o.withStyle(Style.Error).println("ERROR: Local folder ${localFile} doesn't exist. Mounted volume will not be visible to container")
                       throw new IllegalStateException("Local folder ${localFile} doesn't exist.")
                    }

                    args.add('-v')
                    args.add("${localFile.absolutePath}:${volume.value}")
                }
                args.addAll(ext.env.collect{ k, v -> ['-e', "${k}=${v}"] }.flatten())
                args.add('--name')
                args.add(ext.name)
                if (!ext.arguments.isEmpty()) {
                    args.addAll(ext.arguments)
                }
                args.add(ext.image)
                if (!ext.command.isEmpty()) {
                    args.addAll(ext.command)
                }
                commandLine args
            }

            dockerStop.configure {
                commandLine 'docker', 'stop', ext.name
            }

            dockerRemoveContainer.configure {
                commandLine 'docker', 'rm', ext.name
            }
        }
    }
}
