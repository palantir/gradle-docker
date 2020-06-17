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

package com.palantir.gradle.docker;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;

public class DockerComponent implements SoftwareComponentInternal {
    private final UsageContext runtimeUsage;
    private final Set<PublishArtifact> artifacts = new LinkedHashSet<>();
    private final DependencySet runtimeDependencies;

    public DockerComponent(
            PublishArtifact dockerArtifact,
            DependencySet runtimeDependencies,
            ObjectFactory objectFactory,
            ImmutableAttributesFactory attributesFactory) {
        artifacts.add(dockerArtifact);
        this.runtimeDependencies = runtimeDependencies;
        Usage usage = objectFactory.named(Usage.class, Usage.JAVA_RUNTIME);
        ImmutableAttributes attributes = attributesFactory.of(Usage.USAGE_ATTRIBUTE, usage);
        runtimeUsage = new RuntimeUsageContext(usage, attributes);
    }

    @Override
    public final String getName() {
        return "docker";
    }

    @Override
    public final Set<UsageContext> getUsages() {
        return Collections.singleton(runtimeUsage);
    }

    private class RuntimeUsageContext implements UsageContext {

        private final Usage usage;
        private final ImmutableAttributes attributes;

        private RuntimeUsageContext(Usage usage, ImmutableAttributes attributes) {
            this.usage = usage;
            this.attributes = attributes;
        }

        @Override
        public Usage getUsage() {
            return usage;
        }

        @Override
        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            return runtimeDependencies.withType(ModuleDependency.class);
        }

        @Override
        public String getName() {
            return "runtime";
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }

        @Override
        public Set<? extends DependencyConstraint> getDependencyConstraints() {
            return Collections.emptySet();
        }

        @Override
        public Set<? extends Capability> getCapabilities() {
            return Collections.emptySet();
        }

        @Override
        public Set<ExcludeRule> getGlobalExcludes() {
            return Collections.emptySet();
        }
    }
}
