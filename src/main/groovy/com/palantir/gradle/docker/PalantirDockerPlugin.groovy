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

import com.google.common.base.Preconditions
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
    void apply(Project project) {
        DockerExtension ext = project.extensions.create('docker', DockerExtension, project)
        project.configurations.create("docker")

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

        Copy generateDockerCompose = project.tasks.create('generateDockerCompose', Copy, {
            description = 'Populates docker-compose.yml.template file with image versions specified by "docker" ' +
                'dependencies'
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

            // Configure docker-compose templating
            if (ext.resolvedDockerComposeTemplate.exists()) {
                def dockerDependencies = project.configurations.docker.resolvedConfiguration.resolvedArtifacts
                def templateTokens = dockerDependencies.collectEntries {
                    def version = it.moduleVersion.id
                    [("{{${version.group}:${version.name}}}"): version.version]
                }

                generateDockerCompose.with {
                    from(ext.resolvedDockerComposeTemplate)
                    into(ext.resolvedDockerComposeFile.parentFile)
                    rename { fileName -> fileName.replace(
                        ext.resolvedDockerComposeTemplate.name, ext.resolvedDockerComposeFile.name) }
                    filter { String line -> replaceAll(line, templateTokens, ext) }
                }
            }
        }
    }

    /** Replaces all occurrences of templatesTokens's keys by their corresponding values in the given line. */
    static def replaceAll(String line, Map<String, String> templateTokens, DockerExtension ext) {
        templateTokens.each { mapping -> line = line.replace(mapping.key, mapping.value) }
        def unmatchedTokens = line.findAll(/\{\{.*\}\}/)
        Preconditions.checkState(unmatchedTokens.size() == 0,
            "Failed to resolve Docker dependencies mention in %s: %s",
            ext.resolvedDockerComposeTemplate, unmatchedTokens)
        line
    }
}
