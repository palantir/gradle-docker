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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip

class PalantirDockerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerExtension ext = project.extensions.create('docker', DockerExtension, project)
        if (!project.configurations.findByName('docker')) {
            project.configurations.create('docker')
        }

        Delete clean = project.tasks.create('dockerClean', Delete, {
            description = "Cleans Docker build directory."
        })

        Copy prepare = project.tasks.create('dockerPrepare', Copy, {
            description = "Prepares Docker build directory."
            dependsOn clean
        })

        Exec exec = project.tasks.create('docker', Exec, {
            description = "Builds Docker image."
            dependsOn prepare
        })

        Exec push = project.tasks.create('dockerPush', Exec, {
            description = "Pushes Docker image to configured Docker Hub."
            dependsOn exec
        })

        Zip dockerfileZip = project.tasks.create('dockerfileZip', Zip, {
            description = "Bundles the configured Dockerfile in a ZIP file"
        })

        PublishArtifact dockerArtifact = new ArchivePublishArtifact(dockerfileZip)
        Configuration dockerConfiguration = project.getConfigurations().getByName("docker")
        dockerConfiguration.getArtifacts().add(dockerArtifact)
        project.getComponents().add(new DockerComponent(dockerArtifact, dockerConfiguration.getAllDependencies()))

        project.afterEvaluate {
            ext.resolvePathsAndValidate()
            String dockerDir = "${project.buildDir}/docker"

            clean.delete dockerDir

            prepare.with {
                from(ext.resolvedDockerfile) {
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

            dockerfileZip.with {
                from(ext.resolvedDockerfile)
            }
        }
    }
}
