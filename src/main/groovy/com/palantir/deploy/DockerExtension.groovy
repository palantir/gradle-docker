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
package com.palantir.deploy

import org.gradle.api.Task

import com.google.common.collect.ImmutableSet

class DockerExtension {
    private String name = null
    private String dockerfile = null
    private Set<Task> dependencies = ImmutableSet.of()
    private Set<String> files = ImmutableSet.of()

    private File resolvedDockerfile = null
    private Set<File> resolvedFiles = null

    public void setName(String name) {
        this.name = name
    }

    public String getName() {
        return name
    }

    public void setDockerfile(String dockerfile) {
        this.dockerfile = dockerfile
    }

    public void dependsOn(Task... args) {
        this.dependencies = ImmutableSet.copyOf(args)
    }

    public Set<Task> getDependencies() {
        return dependencies
    }

    public void files(String... args) {
        this.files = ImmutableSet.copyOf(files);
    }

    public File getResolvedDockerfile() {
        return resolvedDockerfile
    }

    public Set<String> getResolvedFiles() {
        return resolvedFiles
    }

    public void resolvePathsAndValidate(File projectDir) {
        checkName(name)

        if (dockerfile == null) {
            dockerfile = 'Dockerfile'
        }

        resolvedDockerfile = new File(dockerfile)
        if (!resolvedDockerfile.isAbsolute()) {
            resolvedDockerfile = new File(projectDir, dockerfile)
        }

        checkFileExists(resolvedDockerfile, "dockerfile '%s' does not exist.", dockerfile)

        ImmutableSet.Builder<File> builder = ImmutableSet.builder()
        for (String file : files) {
            File resFile = new File(file)
            if (!resFile.isAbsolute()) {
                resFile = new File(projectDir, file)
            }
            checkFileExists(resFile, "file '%s' does not exist.", file)
            builder.add(resFile)
        }

        resolvedFiles = builder.build()
    }

    private static void checkName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is a required docker configuration item.")
        }
    }

    private static void checkFileExists(File resFile, String message, Object... args) {
        if (!resFile.exists()) {
            throw new IllegalArgumentException(String.format(message, args))
        }
    }

}