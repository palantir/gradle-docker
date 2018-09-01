package com.palantir.gradle.docker

class DockerLoginExtension {

    private String repository = null
    private String username = null
    private String password = null

    DockerLoginExtension() {
    }

    String getUsername() {
        return username
    }

    void setUsername(String username) {
        this.username = username
    }

    String getPassword() {
        return password
    }

    void setPassword(String password) {
        this.password = password
    }

    String getRepository() {
        return repository
    }

    void setRepository(String repository) {
        this.repository = repository
    }
}
