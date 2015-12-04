package com.palantir.gradle.docker;

import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.Usage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DockerComponent implements SoftwareComponentInternal {
    private final Usage runtimeUsage = new RuntimeUsage();
    private final LinkedHashSet<PublishArtifact> artifacts = new LinkedHashSet<>();
    private final DependencySet runtimeDependencies;

    public DockerComponent(PublishArtifact dockerArtifact, DependencySet runtimeDependencies) {
        artifacts.add(dockerArtifact);
        this.runtimeDependencies = runtimeDependencies;
    }

    public String getName() {
        return "docker";
    }

    public Set<Usage> getUsages() {
        return Collections.singleton(runtimeUsage);
    }

    private class RuntimeUsage implements Usage {
        public String getName() {
            return "runtime";
        }

        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }

        public Set<ModuleDependency> getDependencies() {
            return runtimeDependencies.withType(ModuleDependency.class);
        }
    }
}
