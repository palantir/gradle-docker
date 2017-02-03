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

import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DockerComponent implements SoftwareComponentInternal {
    private final UsageContext runtimeUsage = new RuntimeUsageContext();
    private final LinkedHashSet<PublishArtifact> artifacts = new LinkedHashSet<>();
    private final DependencySet runtimeDependencies;

    public DockerComponent(PublishArtifact dockerArtifact, DependencySet runtimeDependencies) {
        artifacts.add(dockerArtifact);
        this.runtimeDependencies = runtimeDependencies;
    }

    @Override
    public String getName() {
        return "docker";
    }

    @Override
    public Set<UsageContext> getUsages() {
        return Collections.singleton(runtimeUsage);
    }

    private class RuntimeUsageContext implements UsageContext {
        @Override
        public Usage getUsage() {
            return Usage.FOR_RUNTIME;
        }

        @Override
        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            return runtimeDependencies.withType(ModuleDependency.class);
        }
    }
}
