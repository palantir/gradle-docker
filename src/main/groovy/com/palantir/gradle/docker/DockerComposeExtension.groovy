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

import org.gradle.api.Project

import com.google.common.base.Preconditions

class DockerComposeExtension {
    Project project

    private String template = 'docker-compose.yml.template'
    private String dockerComposeFile = 'docker-compose.yml'

    private File resolvedDockerComposeTemplate = null
    private File resolvedDockerComposeFile = null

    public DockerComposeExtension(Project project) {
        this.project = project
    }

    public void setTemplate(String dockerComposeTemplate) {
        this.template = dockerComposeTemplate
        Preconditions.checkArgument(project.file(dockerComposeTemplate).exists(),
            "Could not find specified template file: %s", project.file(dockerComposeTemplate))
    }

    public void setDockerComposeFile(String dockerComposeFile) {
        this.dockerComposeFile = dockerComposeFile
    }

    File getResolvedDockerComposeTemplate() {
        return resolvedDockerComposeTemplate
    }

    File getResolvedDockerComposeFile() {
        return resolvedDockerComposeFile
    }

    public void resolvePathsAndValidate() {
        resolvedDockerComposeFile = project.file(dockerComposeFile)
        resolvedDockerComposeTemplate = project.file(template)
    }
}
