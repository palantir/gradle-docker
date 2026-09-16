/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.docker;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class DockerComposeTaskDescriptionsTest {
    @BeforeEach
    void beforeEach(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.docker-compose");
    }

    @Test
    void task_report_includes_descriptions_from_configured_files(RootProject rootProject, GradleInvoker gradle) {
        rootProject.buildGradle().append("""
            dockerCompose {
                template 'templates/custom-template.yml'
                dockerComposeFile 'compose/custom-compose.yml'
            }
            """);

        assertThat(gradle.withArgs("tasks", "--all").buildsSuccessfully())
                .output()
                .contains("dockerComposeUp - Executes `docker-compose` using custom-compose.yml")
                .contains("generateDockerCompose - Populates custom-template.yml file with versions"
                        + " of dependencies from the 'docker' configuration");
    }

    @Test
    void task_report_preserves_custom_descriptions(RootProject rootProject, GradleInvoker gradle) {
        rootProject.buildGradle().append("""
            tasks.named('dockerComposeUp') {
                description = 'Start the development services'
            }
            tasks.named('generateDockerCompose') {
                description = 'Prepare the development services'
            }
            """);

        assertThat(gradle.withArgs("tasks", "--all").buildsSuccessfully())
                .output()
                .contains("dockerComposeUp - Start the development services")
                .contains("generateDockerCompose - Prepare the development services");
    }
}
