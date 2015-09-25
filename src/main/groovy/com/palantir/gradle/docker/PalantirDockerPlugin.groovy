/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
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
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec

class PalantirDockerPlugin implements Plugin<Project> {
    void apply(Project project) {
        DockerExtension ext = project.extensions.create('docker', DockerExtension)

        Task clean = project.tasks.create('dockerClean', Delete, {
            description = "Cleans Docker build directory."
        })

        Task prepare = project.tasks.create('dockerPrepare', Copy, {
            description = "Prepares Docker build directory."
            dependsOn clean
        })

        Task exec = project.tasks.create('docker', Exec, {
            description = "Builds Docker image."
            dependsOn prepare
        })

        Task push = project.tasks.create('dockerPush', Exec, {
            description = "Pushes Docker image to configured Docker Hub."
            dependsOn exec
        })

        project.afterEvaluate {
            ext.resolvePathsAndValidate(project.projectDir)
            String dockerDir = "${project.buildDir}/docker"

            clean.delete dockerDir

            prepare.with {
                from (ext.resolvedDockerfile) {
                    rename { fileName ->
                        fileName.replace(ext.resolvedDockerfile.getName(), 'Dockerfile')
                    }
                }
                from ext.dependencies*.outputs
                from ext.resolvedFiles
                into dockerDir
            }

            exec.with {
                workingDir dockerDir
                commandLine 'docker', 'build', '-t', ext.name, '.'
                dependsOn ext.getDependencies()
            }

            push.with {
                workingDir dockerDir
                commandLine 'docker', 'push', ext.name
            }
        }
    }
}
