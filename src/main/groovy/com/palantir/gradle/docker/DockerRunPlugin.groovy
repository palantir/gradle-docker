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
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import static org.gradle.logging.StyledTextOutput.Style

import java.io.File
import java.util.Map.Entry

import com.google.common.collect.Lists


class DockerRunPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerRunExtension ext = project.extensions.create('dockerRun', DockerRunExtension)

        Task setup = DockerSetupTask.getOrInstall(project)

        Exec dockerRunStatus = project.tasks.create('dockerRunStatus', Exec, {
            group = 'Docker Run'
            description = 'Checks the run status of the container'
            dependsOn setup
        })

        Exec dockerRun = project.tasks.create('dockerRun', Exec, {
            group = 'Docker Run'
            description = 'Runs the specified container with port mappings'
            dependsOn setup
        })

        Exec dockerStop = project.tasks.create('dockerStop', Exec, {
            group = 'Docker Run'
            description = 'Stops the named container if it is running'
            ignoreExitValue = true
            dependsOn setup
        })

        Exec dockerRemoveContainer = project.tasks.create('dockerRemoveContainer', Exec, {
            group = 'Docker Run'
            description = 'Removes the persistent container associated with the Docker Run tasks'
            ignoreExitValue = true
            dependsOn setup
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
                for (String port : ext.ports) {
                    args.add('-p')
                    args.add(port)
                }
                for (Entry<String,String> volume : ext.volumes.entrySet()) {
                    File localFile = new File(project.projectDir, volume.key)

                    if (!localFile.exists()) {
                       StyledTextOutput o = project.services.get(StyledTextOutputFactory).create(DockerRunPlugin)
                       o.withStyle(Style.Error).println("ERROR: Local folder ${localFile} doesn't exist. Mounted volume will not be visible to container")
                       throw new IllegalStateException("Local folder ${localFile} doesn't exist.")

                    }

                    args.add('-v')
                    args.add("${localFile.absolutePath}:${volume.value}")
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
