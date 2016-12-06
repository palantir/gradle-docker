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
import org.gradle.api.artifacts.Configuration

class DockerComposePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerComposeExtension ext =
            project.extensions.create('dockerCompose', DockerComposeExtension, project)
        Configuration dockerConfiguration = project.configurations.maybeCreate('docker')

        project.tasks.create('generateDockerCompose', GenerateDockerCompose, {
            it.ext = ext
            it.configuration = dockerConfiguration
        })
    }
}
