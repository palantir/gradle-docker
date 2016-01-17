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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.os.OperatingSystem

class PalantirDockerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerExtension ext = project.extensions.create('docker', DockerExtension, project)
        if (!project.configurations.findByName('docker')) {
            project.configurations.create('docker')
        }

        Task setup = project.tasks.create('dockerSetup', {
            group = 'Docker'
            description = 'Verify that Docker-related environment variables are set'
            doFirst {
                if (!OperatingSystem.current().isLinux()) {
                    def dockerTlsVerify = System.getenv('DOCKER_TLS_VERIFY')
                    def dockerHost = System.getenv('DOCKER_HOST')
                    def dockerCertPath = System.getenv('DOCKER_CERT_PATH')
                    def dockerMachineName = System.getenv('DOCKER_MACHINE_NAME')
                    def error = ''
                    if (!System.env.DOCKER_TLS_VERIFY) {
                        error += 'DOCKER_TLS_VERIFY not set.\n'
                    }
                    if (!System.env.DOCKER_HOST) {
                        error += 'DOCKER_HOST not set.\n'
                    }
                    if (!System.env.DOCKER_CERT_PATH) {
                        error += 'DOCKER_CERT_PATH not set.\n'
                    }
                    if (!System.env.DOCKER_MACHINE_NAME) {
                        error += 'DOCKER_MACHINE_NAME not set.\n'
                    }
                    if (error != '') {
                        throw new GradleException(error += 'Please make sure your Docker VM is running.')
                    }
                }
            }
        })

        Delete clean = project.tasks.create('dockerClean', Delete, {
            group = 'Docker'
            description = 'Cleans Docker build directory.'
        })

        Copy prepare = project.tasks.create('dockerPrepare', Copy, {
            group = 'Docker'
            description = 'Prepares Docker build directory.'
            dependsOn clean
        })

        Exec exec = project.tasks.create('docker', Exec, {
            group = 'Docker'
            description = 'Builds Docker image.'
            dependsOn prepare, setup
        })

        Exec push = project.tasks.create('dockerPush', Exec, {
            group = 'Docker'
            description = 'Pushes named Docker image to configured Docker Hub.'
            dependsOn exec
        })

        Zip dockerfileZip = project.tasks.create('dockerfileZip', Zip, {
            group = 'Docker'
            description = 'Bundles the configured Dockerfile in a zip file'
        })

        PublishArtifact dockerArtifact = new ArchivePublishArtifact(dockerfileZip)
        Configuration dockerConfiguration = project.getConfigurations().getByName('docker')
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

            if (!ext.tags.isEmpty()) {
                Task tag = project.tasks.create('dockerTag', {
                    group = 'Docker'
                    description = 'Applies all tags to the Docker image.'
                })

                for (String tagName : ext.tags) {
                    String taskTagName = ucfirst(tagName)
                    Exec subTask = project.tasks.create('dockerTag' + taskTagName, Exec, {
                        group = 'Docker'
                        description = "Tags Docker image with tag '${tagName}'"
                        workingDir dockerDir
                        commandLine 'docker', 'tag', '--force=true', ext.name, computeName(ext.name, tagName)
                        dependsOn exec
                    })
                    tag.dependsOn subTask

                    project.tasks.create('dockerPush' + taskTagName, Exec, {
                        group = 'Docker'
                        description = "Pushes the Docker image with tag '${tagName}' to configured Docker Hub"
                        workingDir dockerDir
                        commandLine 'docker', 'push', computeName(ext.name, tagName)
                        dependsOn tag
                    })
                }
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

    private String computeName(String name, String tag) {
        return name.replaceAll(":.*", "") + ":" + tag
    }

    private String ucfirst(String str) {
        StringBuffer sb = new StringBuffer(str);
        sb.replace(0, 1, str.substring(0, 1).toUpperCase());
        return sb.toString();
    }

}
