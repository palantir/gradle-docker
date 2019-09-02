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

import com.palantir.gradle.docker.run.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension

class DockerRunPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerRunExtension ext = project.extensions.create('dockerRun', DockerRunExtension, project)

        ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties()

        extraProperties.set(DockerRun.class.getSimpleName(), DockerRun.class)
        extraProperties.set(DockerRunStatus.class.getSimpleName(), DockerRunStatus.class)
        extraProperties.set(DockerStop.class.getSimpleName(), DockerStop.class)
        extraProperties.set(DockerRemoveContainer.class.getSimpleName(), DockerRemoveContainer.class)
        extraProperties.set(DockerNetworkModeStatus.class.getSimpleName(), DockerNetworkModeStatus.class)

        project.tasks.create('dockerRun', DockerRun.class)
        project.tasks.create('dockerStop', DockerStop.class)
        project.tasks.create('dockerRemoveContainer', DockerRemoveContainer.class)
        project.tasks.create('dockerNetworkModeStatus', DockerNetworkModeStatus.class)
        project.tasks.create('dockerRunStatus', DockerRunStatus.class)
    }
}
