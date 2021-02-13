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

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import com.google.common.base.Preconditions

import groovy.transform.Memoized
import groovy.util.logging.Slf4j

@Slf4j
class GenerateDockerCompose extends DefaultTask {

    @Internal
    Configuration configuration

    GenerateDockerCompose() {
        group = 'Docker'
    }

    @TaskAction
    void run() {
        if (!template.file) {
            throw new IllegalStateException("Could not find specified template file ${template.file}")
        }
        def templateTokens = moduleDependencies.collectEntries {
            [("{{${it.group}:${it.name}}}"): it.version]
        }

        templateTokens.putAll(extraTemplateTokens.collectEntries {
            [("{{${it.key}}}"): it.value]
        })

        dockerComposeFile.withPrintWriter { writer ->
            template.eachLine { line ->
                writer.println this.replaceAll(line, templateTokens)
            }
        }
    }

    @Internal
    @Override
    String getDescription() {
        def defaultDescription = "Populates ${dockerComposeExtension.template.name} file with versions" +
                " of dependencies from the '${configuration.name}' configuration"
        return super.description ?: defaultDescription
    }

    @Input
    Set<ModuleVersionIdentifier> getModuleDependencies() {
        return memoizedModuleDependencies();
    }

    @Memoized
    Set<ModuleVersionIdentifier> memoizedModuleDependencies() {
        log.info "Resolving Docker template dependencies from configuration ${configuration.name}..."
        return configuration.resolvedConfiguration
                .resolvedArtifacts
                *.moduleVersion
                *.id
                .toSet()
    }

    @Input
    Map<String, String> getExtraTemplateTokens() {
        return dockerComposeExtension.templateTokens
    }

    @InputFiles
    File getTemplate() {
        return dockerComposeExtension.template
    }

    @OutputFile
    File getDockerComposeFile() {
        return dockerComposeExtension.dockerComposeFile
    }

    @Internal
    DockerComposeExtension getDockerComposeExtension() {
        return project.extensions.findByType(DockerComposeExtension)
    }

    /** Replaces all occurrences of templatesTokens's keys by their corresponding values in the given line. */
    // Protected to work around GRADLE-1439
    protected String replaceAll(String line, Map<String, String> templateTokens) {
        templateTokens.each { mapping -> line = line.replace(mapping.key, mapping.value) }
        def unmatchedTokens = line.findAll(/\{\{.*\}\}/)
        Preconditions.checkState(unmatchedTokens.size() == 0,
                "Failed to resolve Docker dependencies declared in %s: %s. Known dependencies: %s",
                template, unmatchedTokens, templateTokens)
        return line
    }
}
