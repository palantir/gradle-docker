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

import com.google.common.collect.Maps
import org.gradle.api.Project

class DockerComposeExtension {
    private Project project

    private File template
    private File dockerComposeFile
    private Map<String, String> templateTokens = Maps.newHashMap()

    public DockerComposeExtension(Project project) {
        this.project = project
        this.template = project.file('docker-compose.yml.template')
        this.dockerComposeFile = project.file('docker-compose.yml')
    }

    public void setTemplate(Object dockerComposeTemplate) {
        this.template = project.file(dockerComposeTemplate)
    }

    public void setDockerComposeFile(Object dockerComposeFile) {
        this.dockerComposeFile = project.file(dockerComposeFile)
    }

    public void setTemplateTokens(Map<String, String> templateTokens) {
        this.templateTokens = templateTokens
    }

    Map<String, String> getTemplateTokens() {
        return templateTokens
    }

    File getTemplate() {
        return template
    }

    File getDockerComposeFile() {
        return dockerComposeFile
    }
}
