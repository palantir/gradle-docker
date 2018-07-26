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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip

import javax.inject.Inject
import java.util.regex.Pattern

class PalantirDockerPlugin implements Plugin<Project> {

    private static final Logger log = Logging.getLogger(PalantirDockerPlugin.class)
    private static final Pattern LABEL_KEY_PATTERN = Pattern.compile('^[a-z0-9.-]*$')

    private final ObjectFactory objectFactory
    private final ImmutableAttributesFactory attributesFactory

    @Inject
    PalantirDockerPlugin(ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory) {
        this.objectFactory = objectFactory
        this.attributesFactory = attributesFactory
    }

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

        Task tag = project.tasks.create('dockerTag', {
          group = 'Docker'
          description = 'Applies all tags to the Docker image.'
          dependsOn exec
        })

        Exec push = project.tasks.create('dockerPush', Exec, {
            group = 'Docker'
            description = 'Pushes named Docker image to configured Docker Hub.'
            dependsOn tag
        })

        Zip dockerfileZip = project.tasks.create('dockerfileZip', Zip, {
            group = 'Docker'
            baseName 'dockerfile'
            description = 'Bundles the configured Dockerfile in a zip file'
        })

        PublishArtifact dockerArtifact = new ArchivePublishArtifact(dockerfileZip)
        Configuration dockerConfiguration = project.getConfigurations().getByName('docker')
        dockerConfiguration.getArtifacts().add(dockerArtifact)
        project.getComponents().add(new DockerComponent(dockerArtifact, dockerConfiguration.getAllDependencies(),
                objectFactory, attributesFactory))

        project.afterEvaluate {
            ext.resolvePathsAndValidate()
            String dockerDir = "${project.buildDir}/docker"
            clean.delete dockerDir

            prepare.with {
                with ext.copySpec
                from(ext.resolvedDockerfile) {
                    rename { fileName ->
                        fileName.replace(ext.resolvedDockerfile.getName(), 'Dockerfile')
                    }
                }
                into dockerDir
            }

            exec.with {
                workingDir dockerDir
                commandLine buildCommandLine(ext)
                dependsOn ext.getDependencies()
                logging.captureStandardOutput LogLevel.INFO
                logging.captureStandardError LogLevel.ERROR
            }

            if (!ext.tags.isEmpty()) {

                ext.tags.each { tagName ->
                    String taskTagName = ucfirst(tagName)
                    Exec subTask = project.tasks.create('dockerTag' + taskTagName, Exec, {
                        group = 'Docker'
                        description = "Tags Docker image with tag '${tagName}'"
                        workingDir dockerDir
                        commandLine 'docker', 'tag', "${ -> ext.name}", "${ -> computeName(ext.name, tagName)}"
                        dependsOn exec
                    })
                    tag.dependsOn subTask

                    project.tasks.create('dockerPush' + taskTagName, Exec, {
                        group = 'Docker'
                        description = "Pushes the Docker image with tag '${tagName}' to configured Docker Hub"
                        workingDir dockerDir
                        commandLine 'docker', 'push', "${ -> computeName(ext.name, tagName)}"
                        dependsOn subTask
                    })
                }
            }

            push.with {
                workingDir dockerDir
                commandLine 'docker', 'push', "${ -> ext.name}"
            }

            dockerfileZip.with {
                from(ext.resolvedDockerfile)
            }
        }
    }

    private List<String> buildCommandLine(DockerExtension ext) {
        List<String> buildCommandLine = ['docker', 'build']
        if (ext.noCache) {
            buildCommandLine.add '--no-cache'
        }
        if (!ext.buildArgs.isEmpty()) {
            for (Map.Entry<String, String> buildArg : ext.buildArgs.entrySet()) {
                buildCommandLine.addAll('--build-arg', "${buildArg.getKey()}=${buildArg.getValue()}")
            }
        }
        if (!ext.labels.isEmpty()) {
            for (Map.Entry<String, String> label : ext.labels.entrySet()) {
                if (!label.getKey().matches(LABEL_KEY_PATTERN)) {
                    throw new GradleException(String.format("Docker label '%s' contains illegal characters. " +
                            "Label keys must only contain lowercase alphanumberic, `.`, or `-` characters (must match %s).",
                            label.getKey(), LABEL_KEY_PATTERN.pattern()))
                }
                buildCommandLine.addAll('--label', "${label.getKey()}=${label.getValue()}")
            }
        }
        if (ext.pull) {
            buildCommandLine.add '--pull'
        }
        buildCommandLine.addAll(['-t', "${ -> ext.name}", '.'])
        return buildCommandLine
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
