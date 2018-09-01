package com.palantir.gradle.docker

class DockerLoginExtension {

    private String repository = "myRepo"
    private String username = "user"
    private String password = "pw"

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
