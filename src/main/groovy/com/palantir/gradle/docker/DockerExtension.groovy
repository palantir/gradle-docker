/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.docker

import org.gradle.api.Project
import org.gradle.api.Task

import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.ImmutableSet

class DockerExtension {
    Project project

    private String name = null
    private String dockerfile = 'Dockerfile'
    private String dockerComposeTemplate = 'docker-compose.yml.template'
    private String dockerComposeFile = 'docker-compose.yml'
    private Set<Task> dependencies = ImmutableSet.of()
    private Set<String> files = ImmutableSet.of()
    private Set<String> tags = ImmutableSet.of()

    private File resolvedDockerfile = null
    private File resolvedDockerComposeTemplate = null
    private File resolvedDockerComposeFile = null
    private Set<File> resolvedFiles = null

    public DockerExtension(Project project) {
        this.project = project
    }

    public void setName(String name) {
        this.name = name
    }

    public String getName() {
        return name
    }

    public void setDockerfile(String dockerfile) {
        this.dockerfile = dockerfile
    }

    public void setDockerComposeTemplate(String dockerComposeTemplate) {
        this.dockerComposeTemplate = dockerComposeTemplate
        Preconditions.checkArgument(project.file(dockerComposeTemplate).exists(),
            "Could not find specified template file: %s", project.file(dockerComposeTemplate))
    }

    public void setDockerComposeFile(String dockerComposeFile) {
        this.dockerComposeFile = dockerComposeFile
    }

    public void dependsOn(Task... args) {
        this.dependencies = ImmutableSet.copyOf(args)
    }

    public Set<Task> getDependencies() {
        return dependencies
    }

    public void files(String... args) {
        this.files = ImmutableSet.copyOf(args);
    }

    public Set<String> getTags() {
        return tags
    }

    public void tags(String... args) {
        this.tags = ImmutableSet.copyOf(args);
    }

    public File getResolvedDockerfile() {
        return resolvedDockerfile
    }

    File getResolvedDockerComposeTemplate() {
        return resolvedDockerComposeTemplate
    }

    File getResolvedDockerComposeFile() {
        return resolvedDockerComposeFile
    }

    public Set<String> getResolvedFiles() {
        return resolvedFiles
    }

    public void resolvePathsAndValidate() {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name),
            "name is a required docker configuration item.")

        resolvedDockerfile = project.file(dockerfile)
        Preconditions.checkArgument(resolvedDockerfile.exists(), "dockerfile '%s' does not exist.", dockerfile)
        resolvedDockerComposeFile = project.file(dockerComposeFile)
        resolvedDockerComposeTemplate = project.file(dockerComposeTemplate)

        ImmutableSet.Builder<File> builder = ImmutableSet.builder()
        for (String file : files) {
            def resFile = project.file(file)
            Preconditions.checkArgument(resFile.exists(), "file '%s' does not exist.", file)
            builder.add(resFile)
        }

        resolvedFiles = builder.build()
    }
}
