package com.palantir.gradle.docker;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

import java.io.File;

public class DockerComposeUp extends DefaultTask {

    private DockerComposeExtension ext;
    private Configuration configuration;

    public DockerComposeUp() {
        this.setGroup("Docker");
    }

    @TaskAction
    public void run() {
        getProject().exec(new Action<ExecSpec>() {
            @Override
            public void execute(ExecSpec execSpec) {
                execSpec.executable("docker-compose");
                execSpec.args("-f", getDockerComposeFile(), "up", "-d");
            }
        });
    }

    @Override
    public String getDescription() {
        return "Executes `docker-compose` using " + getExt().getDockerComposeFile().getName();
    }

    @InputFiles
    public File getDockerComposeFile() {
        return ext.getDockerComposeFile();
    }

    public DockerComposeExtension getExt() {
        return ext;
    }

    public void setExt(DockerComposeExtension ext) {
        this.ext = ext;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}
