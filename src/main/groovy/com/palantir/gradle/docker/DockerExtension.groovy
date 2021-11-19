/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory

class DockerExtension {
    Project project

    private static final String DEFAULT_DOCKERFILE_PATH = 'Dockerfile'
    private String name = null
    private File dockerfile = null
    private String dockerComposeTemplate = 'docker-compose.yml.template'
    private String dockerComposeFile = 'docker-compose.yml'
    private Set<Task> dependencies = ImmutableSet.of()
    private Set<String> tags = ImmutableSet.of()
    private Map<String, String> namedTags = new HashMap<>()
    private Map<String, String> labels = ImmutableMap.of()
    private Map<String, String> buildArgs = ImmutableMap.of()
    private boolean pull = false
    private boolean noCache = false
    private String network = null

    private File resolvedDockerfile = null
    private File resolvedDockerComposeTemplate = null
    private File resolvedDockerComposeFile = null

    private File saveTarget = null
    private String saveTargetName = null

    // The CopySpec defining the Docker Build Context files
    private final CopySpec copySpec

    DockerExtension(Project project) {
        this.project = project
        this.copySpec = project.copySpec()
    }

    void setName(String name) {
        this.name = name
    }

    String getName() {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is a required docker configuration item.")
        return name
    }

    void setDockerfile(File dockerfile) {
        this.dockerfile = dockerfile
    }

    void setDockerComposeTemplate(String dockerComposeTemplate) {
        this.dockerComposeTemplate = dockerComposeTemplate
        Preconditions.checkArgument(project.file(dockerComposeTemplate).exists(),
            "Could not find specified template file: %s", project.file(dockerComposeTemplate))
    }

    void setDockerComposeFile(String dockerComposeFile) {
        this.dockerComposeFile = dockerComposeFile
    }

    void dependsOn(Task... args) {
        this.dependencies = ImmutableSet.copyOf(args)
    }

    Set<Task> getDependencies() {
        return dependencies
    }

    void files(Object... files) {
        copySpec.from(files)
    }

    Set<String> getTags() {
        return tags
    }

    @Deprecated
    void tags(String... args) {
        this.tags = ImmutableSet.copyOf(args)
    }

    Map<String, String> getNamedTags() {
        return ImmutableMap.copyOf(namedTags)
    }

    void tag(String taskName, String tag) {
        if (namedTags.putIfAbsent(taskName, tag) != null) {
            StyledTextOutput o = project.services.get(StyledTextOutputFactory.class).create(DockerExtension)
            o.withStyle(StyledTextOutput.Style.Error).println("WARNING: Task name '${taskName}' is existed.")
        }
    }

    Map<String, String> getLabels() {
        return labels
    }

    void labels(Map<String, String> labels) {
        this.labels = ImmutableMap.copyOf(labels)
    }

    File getResolvedDockerfile() {
        return resolvedDockerfile
    }

    File getResolvedDockerComposeTemplate() {
        return resolvedDockerComposeTemplate
    }

    File getResolvedDockerComposeFile() {
        return resolvedDockerComposeFile
    }

    CopySpec getCopySpec() {
        return copySpec
    }

    void resolvePathsAndValidate() {
        if (dockerfile != null) {
            resolvedDockerfile = dockerfile
        } else {
            resolvedDockerfile = project.file(DEFAULT_DOCKERFILE_PATH)
        }
        resolvedDockerComposeFile = project.file(dockerComposeFile)
        resolvedDockerComposeTemplate = project.file(dockerComposeTemplate)
    }

    Map<String, String> getBuildArgs() {
        return buildArgs
    }

    String getNetwork() {
        return network
    }

    void setNetwork(String network) {
        this.network = network
    }

    void buildArgs(Map<String, String> buildArgs) {
        this.buildArgs = ImmutableMap.copyOf(buildArgs)
    }

    boolean getPull() {
        return pull
    }

    void pull(boolean pull) {
        this.pull = pull
    }

    boolean getNoCache() {
        return noCache
    }

    void noCache(boolean noCache) {
        this.noCache = noCache
    }

    File getSaveTarget() {
        return saveTarget
    }

    void setSaveTarget(File saveTarget) {
        this.saveTarget = saveTarget
    }

    String getSaveTargetName() {
        return saveTargetName
    }

    void setSaveTargetName(String saveTargetName) {
        this.saveTargetName = saveTargetName
    }
}
