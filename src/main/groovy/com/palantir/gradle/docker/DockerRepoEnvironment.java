package com.palantir.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class DockerRepoEnvironment {

    private final String name;
    private Property<String> url;
    private Property<String> user;
    private Property<String> password;

    public DockerRepoEnvironment(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.url = objectFactory.property(String.class);
        this.user = objectFactory.property(String.class);
        this.password = objectFactory.property(String.class);
    }

    public String getName() {
        return name;
    }

    public void setUrl(String url) {
        this.url.set(url);
    }

    public Property<String> getUrl() {
        return url;
    }

    public Property<String> getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user.set(user);
    }

    public Property<String> getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password.set(password);
    }
}
