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


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.tasks.Exec

class DockerRunPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerRunExtension ext = project.extensions.create('dockerRun', DockerRunExtension)

        project.tasks.create('dockerRunStatus', Exec, {
            group = 'Docker Run'
            description = 'Checks the run status of the container'

            project.afterEvaluate {
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
        })


        ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();
        extraProperties.set(DockerRunTask.class.getSimpleName(), DockerRunTask.class);

        project.tasks.create('dockerRun', DockerRunTask.class) {
            conventionMapping.containerName = { project.dockerRun.name }
            conventionMapping.image = { project.dockerRun.image }
            conventionMapping.command = { project.dockerRun.command }
            conventionMapping.network = { project.dockerRun.network }
            conventionMapping.ports = { project.dockerRun.ports }
            conventionMapping.env = { project.dockerRun.env }
            conventionMapping.volumes = { project.dockerRun.volumes }
            conventionMapping.daemonize = { project.dockerRun.daemonize }
            conventionMapping.clean = { project.dockerRun.clean }
        }

        project.tasks.create('dockerStop', Exec, {
            group = 'Docker Run'
            description = 'Stops the named container if it is running'
            ignoreExitValue = true
            project.afterEvaluate {
                commandLine 'docker', 'stop', ext.name
            }
        })

        project.tasks.create('dockerRemoveContainer', Exec, {
            group = 'Docker Run'
            description = 'Removes the persistent container associated with the Docker Run tasks'
            ignoreExitValue = true
            project.afterEvaluate {
                commandLine 'docker', 'rm', ext.name
            }
        })

        project.tasks.create('dockerNetworkModeStatus', Exec, {
            group = 'Docker Run'
            description = 'Checks the network configuration of the container'
            project.afterEvaluate {
                standardOutput = new ByteArrayOutputStream()
                commandLine 'docker', 'inspect', '--format={{.HostConfig.NetworkMode}}', ext.name
                doLast {
                    def networkMode = standardOutput.toString().trim()
                    if (networkMode == 'default') {
                        println "Docker container '${ext.name}' has default network configuration (bridge)."
                    } else {
                        if (networkMode == ext.network) {
                            println "Docker container '${ext.name}' is configured to run with '${ext.network}' network mode."
                        } else {
                            println "Docker container '${ext.name}' runs with '${networkMode}' network mode instead of the configured '${ext.network}'."
                            return 1
                        }
                    }
                }
            }
        })
    }
}
