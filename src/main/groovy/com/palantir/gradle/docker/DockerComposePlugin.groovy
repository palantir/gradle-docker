/*
 * Copyright 2015 Palantir Technologies
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
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

import com.google.common.base.Preconditions

class DockerComposePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerComposeExtension ext =
            project.extensions.create('dockerCompose', DockerComposeExtension, project)
        if (!project.configurations.findByName('docker')) {
            project.configurations.create('docker')
        }

        Task setup = DockerSetupTask.getOrInstall(project)

        Copy generateDockerCompose = project.tasks.create('generateDockerCompose', Copy, {
            group = 'Docker Compose'
            description = 'Populates docker-compose.yml.template file with image versions specified by "docker" ' +
                'dependencies'
        })

        Exec dockerComposeUpStatus = project.tasks.create('dockerComposeUpStatus', Exec, {
            group = 'Docker Compose'
            description = 'Checks the run status of services specified in your docker-compose.yml'
            dependsOn setup
        })

        Exec dockerComposeUp = project.tasks.create('dockerComposeUp', Exec, {
            group = 'Docker Compose'
            description = 'Builds and starts containers defined in your docker-compose.yml'
            dependsOn generateDockerCompose
            dependsOn setup
        })

        Exec dockerComposeStop = project.tasks.create('dockerComposeStop', Exec, {
            group = 'Docker Compose'
            description = 'Stops containers started by dockerComposeUp'
            dependsOn setup
        })

        Exec dockerComposeRemove = project.tasks.create('dockerComposeRemove', Exec, {
            group = 'Docker Compose'
            description = 'Removes stopped containers'
            dependsOn dockerComposeStop
            dependsOn setup
        })

        project.afterEvaluate {
            ext.resolvePathsAndValidate()
            if (ext.resolvedDockerComposeTemplate.exists()) {
                def dockerDependencies = project.configurations.docker.resolvedConfiguration.resolvedArtifacts
                def templateTokens = dockerDependencies.collectEntries {
                    def version = it.moduleVersion.id
                    [("{{${version.group}:${version.name}}}"): version.version]
                }

                generateDockerCompose.with {
                    from(ext.resolvedDockerComposeTemplate)
                    into(ext.resolvedDockerComposeFile.parentFile)
                    rename { fileName ->
                        fileName.replace(
                            ext.resolvedDockerComposeTemplate.name, ext.resolvedDockerComposeFile.name)
                    }
                    filter { String line -> replaceAll(line, templateTokens, ext) }
                }
            }

            dockerComposeUpStatus.with {
                standardOutput = new ByteArrayOutputStream()
                commandLine 'docker-compose', '-f', "${ext.resolvedDockerComposeFile.name}", 'ps', '-q'
                doLast {
                    def containerIds = standardOutput.toString().readLines()
                    def containerNames = []
                    containerIds.each { containerId ->
                        // removing first character of container name because it is always a '/'
                        containerNames.add("docker inspect --format={{.Name}} $containerId".execute().text.substring(1).trim())
                    }
                    def containerStatuses = "docker inspect --format={{.State.Running}} ${standardOutput.toString()}".execute().text.readLines()

                    containerStatuses.eachWithIndex { status, i ->
                        if ("$status" =~ /(false)/) {
                            println "Service '${containerNames[i]}' is STOPPED."
                        } else {
                            println "Service '${containerNames[i]}' is RUNNING."
                        }
                    }
                }
            }

            dockerComposeUp.with {
                commandLine 'docker-compose', '-f', "${ext.resolvedDockerComposeFile.name}", 'up', '-d'
            }

            dockerComposeStop.with {
                commandLine 'docker-compose', '-f', "${ext.resolvedDockerComposeFile.name}", 'stop'
            }

            dockerComposeRemove.with {
                commandLine 'docker-compose', '-f', "${ext.resolvedDockerComposeFile.name}", 'rm', '-f'
            }
        }
    }

    /** Replaces all occurrences of templatesTokens's keys by their corresponding values in the given line. */
    static def replaceAll(String line, Map<String, String> templateTokens, DockerComposeExtension ext) {
        templateTokens.each { mapping -> line = line.replace(mapping.key, mapping.value) }
        def unmatchedTokens = line.findAll(/\{\{.*\}\}/)
        Preconditions.checkState(unmatchedTokens.size() == 0,
            "Failed to resolve Docker dependencies declared in %s: %s. Known dependencies: %s",
            ext.resolvedDockerComposeTemplate, unmatchedTokens, templateTokens)
        return line
    }
}
