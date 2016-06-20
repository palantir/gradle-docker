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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class DockerSetupTask extends DefaultTask {

    @Override
    public String getGroup() {
        return 'Docker'
    }

    @Override
    public String getDescription() {
        return 'Verify that Docker daemon is running.'
    }

    @TaskAction
    public void action() {
        project.exec {
            executable "docker"
            args "version"
        }
    }

    public static DockerSetupTask getOrInstall(Project project) {
        DockerSetupTask setup = project.tasks.findByName('dockerSetup')
        if (setup == null) {
            setup = project.tasks.create('dockerSetup', DockerSetupTask)
        }
        return setup
    }

}
