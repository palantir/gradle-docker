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

import com.google.common.collect.ImmutableMap
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class DockerComposePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create('dockerCompose', DockerComposeExtension, project)
        Configuration dockerConfiguration = project.configurations.maybeCreate('docker')

        // sls-packaging adds a 'productDependencies' configuration, which contains the inferred lower bounds of products
        // you depend on.  We wire it up automatically, so all users don't need to add:
        //
        //    dependencies {
        //        docker project(path: ':foo', configuration: 'productDependencies')
        //    }
        project.subprojects({ Project subproject ->
            subproject.getPlugins().withId("com.palantir.product-dependency-introspection", {
                dockerConfiguration.dependencies.add(subproject.dependencies.project(ImmutableMap.of(
                        "path", subproject.path,
                        "configuration", "productDependencies"
                )))
            })
        })
        project.getPlugins().withId("com.palantir.product-dependency-introspection", {
            dockerConfiguration.extendsFrom(project.configurations.getByName('productDependencies'))
        })

        project.tasks.create('generateDockerCompose', GenerateDockerCompose, {
            it.configuration = dockerConfiguration
        })

        project.tasks.create('dockerComposeUp', DockerComposeUp, {
            it.configuration = dockerConfiguration
        })

        project.tasks.create('dockerComposeDown', DockerComposeDown, {
            it.configuration = dockerConfiguration
        })
    }
}
