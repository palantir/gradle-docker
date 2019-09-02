/*
 * Copyright 2016 Palantir Technologies
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

import com.palantir.gradle.docker.run.DockerNetworkModeStatus
import com.palantir.gradle.docker.run.DockerRemoveContainer
import com.palantir.gradle.docker.run.DockerRunStatus
import com.palantir.gradle.docker.run.DockerRun
import com.palantir.gradle.docker.run.DockerStop
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension

class DockerRunPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerRunExtension ext = project.extensions.create('dockerRun', DockerRunExtension)

        ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();

        extraProperties.set(DockerRun.class.getSimpleName(), DockerRun.class);

        project.tasks.create('dockerRunStatus', DockerRunStatus.class) {
            conventionMapping.containerName = { project.dockerRun.name }
        }

        project.tasks.create('dockerRun', DockerRun.class) {
            conventionMapping.containerName = { project.dockerRun.name }
            conventionMapping.image = { project.dockerRun.image }
            conventionMapping.command = { project.dockerRun.command }
            conventionMapping.network = { project.dockerRun.network }
            conventionMapping.ports = { project.dockerRun.ports }
            conventionMapping.env = { project.dockerRun.env }
            conventionMapping.volumes = { project.dockerRun.volumes }
            conventionMapping.daemonize = { project.dockerRun.daemonize }
            conventionMapping.clean = { project.dockerRun.clean }
        }

        project.tasks.create('dockerStop', DockerStop.class) {
            conventionMapping.containerName = { project.dockerRun.name }
        }

        project.tasks.create('dockerRemoveContainer', DockerRemoveContainer.class) {
            conventionMapping.containerName = { project.dockerRun.name }
        }

        project.tasks.create('dockerNetworkModeStatus', DockerNetworkModeStatus.class){
            conventionMapping.containerName = { project.dockerRun.name }
            conventionMapping.network = { project.dockerRun.network }
        }
    }
}
