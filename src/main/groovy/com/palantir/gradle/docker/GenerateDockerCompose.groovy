package com.palantir.gradle.docker

import com.google.common.base.Preconditions
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@Slf4j
class GenerateDockerCompose extends DefaultTask {

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

        templateTokens.putAll(dockerComposeExtension.templateTokens.collectEntries {
            [("{{${it.key}}}"): it.value]
        })

        dockerComposeFile.withPrintWriter { writer ->
            template.eachLine { line ->
                writer.println this.replaceAll(line, templateTokens)
            }
        }
    }

    @Override
    String getDescription() {
        def defaultDescription = "Populates ${dockerComposeExtension.template.name} file with versions" +
                " of dependencies from the '${configuration.name}' configuration"
        return super.description ?: defaultDescription
    }

    @Input
    @Memoized
    Set<ModuleVersionIdentifier> getModuleDependencies() {
        log.info "Resolving Docker template dependencies from configuration ${configuration.name}..."
        return configuration.resolvedConfiguration
            .resolvedArtifacts
            *.moduleVersion
            *.id
            .toSet()
    }

    @InputFiles
    File getTemplate() {
        return dockerComposeExtension.template
    }

    @OutputFile
    File getDockerComposeFile() {
        return dockerComposeExtension.dockerComposeFile
    }

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
