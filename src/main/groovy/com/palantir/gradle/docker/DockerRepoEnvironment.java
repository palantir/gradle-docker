package com.palantir.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class DockerRepoEnvironment {

    private final String name;
    private Property<String> url;

    public DockerRepoEnvironment(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.url = objectFactory.property(String.class);
    }

    public void setUrl(String url) {
        this.url.set(url);
    }

    public String getName() {
        return name;
    }

    public Property<String> getUrl() {
        return url;
    }
}
