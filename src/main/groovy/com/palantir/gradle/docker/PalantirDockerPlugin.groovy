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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip

import static com.palantir.gradle.docker.DockerExtension.isWildcardDirectory

class PalantirDockerPlugin implements Plugin<Project> {

    private static final Logger log = Logging.getLogger(PalantirDockerPlugin.class)

    @Override
    void apply(Project project) {
        DockerExtension ext = project.extensions.create('docker', DockerExtension, project)
        if (!project.configurations.findByName('docker')) {
            project.configurations.create('docker')
        }

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
            dependsOn prepare
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

                ([] + ext.dependencies*.outputs*.getFiles()*.getFiles().flatten() + ext.resolvedFiles).each { File file ->
                    def wildcardDirectory = isWildcardDirectory(file)

                    /**
                     * Supports copying of directories (preserving the directory itself) and also just copying the
                     * content of a directory by using a '*' wildcard (e.g. /tmp/*)
                     */
                    if (wildcardDirectory) {
                        file = file.getParentFile()
                    }

                    from (file) {
                        // Preserve a copied folder if no wildcard is used at the very end
                        if (file.isDirectory() && !wildcardDirectory) {
                            into(file.getName())
                        }
                    }
                }

                if (!ext.resolvedFiles) {
                    // default: copy all files excluding the project buildDir
                    from(project.projectDir) {
                        exclude "${project.buildDir.name}"
                    }
                }
                into dockerDir
            }

            List<String> buildCommandLine = ['docker', 'build']
            if (!ext.buildArgs.isEmpty()) {
                for (Map.Entry<String, String> buildArg : ext.buildArgs.entrySet()) {
                    buildCommandLine.addAll('--build-arg', "${buildArg.getKey()}=${buildArg.getValue()}")
                }
            }
            if (ext.pull) {
                buildCommandLine.add '--pull'
            }
            buildCommandLine.addAll(['-t', ext.name, '.'])
            exec.with {
                workingDir dockerDir
                commandLine buildCommandLine
                dependsOn ext.getDependencies()
                logging.captureStandardOutput LogLevel.INFO
                logging.captureStandardError  LogLevel.ERROR
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
                        commandLine 'docker', 'tag', ext.name, computeName(ext.name, tagName)
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

    private static String computeName(String name, String tag) {
        int lastColon = name.lastIndexOf(':')
        int lastSlash = name.lastIndexOf('/')

        int endIndex;

        // image_name -> this should remain
        // host:port/image_name -> this should remain.
        // host:port/image_name:v1 -> v1 should be replaced
        if (lastColon > lastSlash) endIndex = lastColon
        else endIndex = name.length()

        return name.substring(0, endIndex) + ":" + tag
    }

    private static String ucfirst(String str) {
        StringBuffer sb = new StringBuffer(str);
        sb.replace(0, 1, str.substring(0, 1).toUpperCase());
        return sb.toString();
    }

}
