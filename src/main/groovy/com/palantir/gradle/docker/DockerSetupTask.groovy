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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

class DockerSetupTask extends DefaultTask {

    @Override
    public String getGroup() {
        return 'Docker'
    }

    @Override
    public String getDescription() {
        return 'Verify that Docker-related environment variables are set'
    }

    @TaskAction
    public void action() {
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

    public static DockerSetupTask getOrInstall(Project project) {
        DockerSetupTask setup = project.tasks.findByName('dockerSetup')
        if (setup == null) {
            setup = project.tasks.create('dockerSetup', DockerSetupTask)
        }
        return setup
    }

}
