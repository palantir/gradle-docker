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
import org.gradle.api.tasks.Copy

import com.google.common.base.Preconditions

class DockerComposePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerComposeExtension ext =
            project.extensions.create('dockerCompose', DockerComposeExtension, project)
        if (!project.configurations.findByName('docker')) {
            project.configurations.create('docker')
        }

        Copy generateDockerCompose = project.tasks.create('generateDockerCompose', Copy, {
            description = 'Populates docker-compose.yml.template file with image versions specified by "docker" ' +
                'dependencies'
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
