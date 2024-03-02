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
import com.google.common.collect.Sets
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
    private boolean buildx = false
    private Set<String> platform = ImmutableSet.of()
    private boolean load = false
    private boolean push = false
    private String builder = null

    private File resolvedDockerfile = null
    private File resolvedDockerComposeTemplate = null
    private File resolvedDockerComposeFile = null

    // The CopySpec defining the Docker Build Context files
    private final CopySpec copySpec

    public DockerExtension(Project project) {
        this.project = project
        this.copySpec = project.copySpec()
    }

    public void setName(String name) {
        this.name = name
    }

    public String getName() {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is a required docker configuration item.")
        return name
    }

    public void setDockerfile(File dockerfile) {
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

    public void files(Object... files) {
        copySpec.from(files)
    }

    public Set<String> getTags() {
        return Sets.union(this.tags, ImmutableSet.of(project.getVersion().toString()))
    }

    @Deprecated
    public void tags(String... args) {
        this.tags = ImmutableSet.copyOf(args)
    }

    public Map<String, String> getNamedTags() {
        return ImmutableMap.copyOf(namedTags)
    }

    public void tag(String taskName, String tag) {
        if (namedTags.putIfAbsent(taskName, tag) != null) {
            StyledTextOutput o = project.services.get(StyledTextOutputFactory.class).create(DockerExtension)
            o.withStyle(StyledTextOutput.Style.Error).println("WARNING: Task name '${taskName}' is existed.")
        }
    }

    public Map<String, String> getLabels() {
        return labels
    }

    public void labels(Map<String, String> labels) {
        this.labels = ImmutableMap.copyOf(labels)
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

    public CopySpec getCopySpec() {
        return copySpec
    }

    public void resolvePathsAndValidate() {
        if (dockerfile != null) {
            resolvedDockerfile = dockerfile
        } else {
            resolvedDockerfile = project.file(DEFAULT_DOCKERFILE_PATH)
        }
        resolvedDockerComposeFile = project.file(dockerComposeFile)
        resolvedDockerComposeTemplate = project.file(dockerComposeTemplate)
    }

    public Map<String, String> getBuildArgs() {
        return buildArgs
    }

    public String getNetwork() {
        return network
    }

    public void setNetwork(String network) {
        this.network = network
    }

    public void buildArgs(Map<String, String> buildArgs) {
        this.buildArgs = ImmutableMap.copyOf(buildArgs)
    }

    public boolean getPull() {
        return pull
    }

    public void pull(boolean pull) {
        this.pull = pull
    }

    public boolean getNoCache() {
        return noCache
    }

    public void noCache(boolean noCache) {
        this.noCache = noCache
    }

    public boolean getLoad() {
        return load
    }

    public void load(boolean load) {
        this.load = load
    }

    public boolean getPush() {
	return push
    }

    public void push(boolean push) {
	this.push = push
    }

    boolean getBuildx() {
        return buildx
    }

    public void buildx(boolean buildx) {
        this.buildx = buildx
    }

    public Set<String> getPlatform() {
        return platform
    }

    public void platform(String... args) {
        this.platform = ImmutableSet.copyOf(args)
    }

    String getBuilder() {
        return builder
    }

    public void builder(String builder) {
        this.builder = builder
    }
}
