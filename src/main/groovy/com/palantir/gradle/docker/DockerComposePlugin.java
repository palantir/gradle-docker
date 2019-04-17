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
package com.palantir.gradle.docker;

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class DockerComposePlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        final DockerComposeExtension ext = project.getExtensions().create("dockerCompose", DockerComposeExtension.class, project);
        final Configuration dockerConfiguration = project.getConfigurations().maybeCreate("docker");

        // sls-packaging adds a 'productDependencies' configuration, which contains the inferred lower bounds of products
        // you depend on.  We wire it up automatically, so all users don't need to add:
        //
        //    dependencies {
        //        docker project(path: ':foo', configuration: 'productDependencies')
        //    }
        project.subprojects(new Action<Project>() {
            @Override
            public void execute(final Project subproject) {
                subproject.getPlugins().withId("com.palantir.product-dependency-introspection", new Action<Plugin>() {
                    @Override
                    public void execute(Plugin unused) {
                        dockerConfiguration.getDependencies().add(
                                subproject.getDependencies().project(ImmutableMap.of(
                                        "path", subproject.getPath(),
                                        "configuration", "productDependencies")));
                    }
                });
            }
        });

        project.getPlugins().withId("com.palantir.product-dependency-introspection", new Action<Plugin>() {
            @Override
            public void execute(Plugin plugin) {
                dockerConfiguration.extendsFrom(project.getConfigurations().getByName("productDependencies"));
            }
        });

        project.getTasks().create("generateDockerCompose", GenerateDockerCompose.class, new Action<GenerateDockerCompose>() {
            @Override
            public void execute(GenerateDockerCompose task) {
                task.setExt(ext);
                task.setConfiguration(dockerConfiguration);
            }
        });

        project.getTasks().create("dockerComposeUp", DockerComposeUp.class, new Action<DockerComposeUp>() {
            @Override
            public void execute(DockerComposeUp task) {
                task.setExt(ext);
                task.setConfiguration(dockerConfiguration);
            }
        });
    }
}
