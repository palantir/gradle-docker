package com.palantir.gradle.docker

import com.google.common.base.Preconditions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy

class DockerComposePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        DockerComposeExtension ext =
            project.extensions.create('dockerCompose', DockerComposeExtension, project)
        project.configurations.create("docker")

        Copy generateDockerCompose = project.tasks.create('generateDockerCompose', Copy, {
            description = 'Populates docker-compose.yml.template file with image versions specified by "docker" ' +
                'dependencies'
        })

        project.afterEvaluate {
            ext.resolvePathsAndValidate()
            if (ext.resolvedDockerComposeTemplate.exists()) {
                def dockerDependencies = project.configurations.docker.resolvedConfiguration.resolvedArtifacts
                def templateTokens = dockerDependencies.collectEntries {
                    def version = it.moduleVersion.id
                    [("{{${version.group}:${version.name}}}"): version.version]
                }

                generateDockerCompose.with {
                    from(ext.resolvedDockerComposeTemplate)
                    into(ext.resolvedDockerComposeFile.parentFile)
                    rename { fileName ->
                        fileName.replace(
                            ext.resolvedDockerComposeTemplate.name, ext.resolvedDockerComposeFile.name)
                    }
                    filter { String line -> replaceAll(line, templateTokens, ext) }
                }
            }
        }
    }

    /** Replaces all occurrences of templatesTokens's keys by their corresponding values in the given line. */
    static def replaceAll(String line, Map<String, String> templateTokens, DockerComposeExtension ext) {
        templateTokens.each { mapping -> line = line.replace(mapping.key, mapping.value) }
        def unmatchedTokens = line.findAll(/\{\{.*\}\}/)
        Preconditions.checkState(unmatchedTokens.size() == 0,
            "Failed to resolve Docker dependencies declared in %s: %s. Known dependencies: %s",
            ext.resolvedDockerComposeTemplate, unmatchedTokens, templateTokens)
        return line
    }
}
