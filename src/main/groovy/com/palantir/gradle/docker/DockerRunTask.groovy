package com.palantir.gradle.docker

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.tasks.AbstractExecTask

import java.lang.reflect.Array

class DockerRunTask extends AbstractExecTask {

//    String name ='dockerRun'
    String nameContainer
    String image
    String network
    List<String> command = ImmutableList.of()
    Set<String> ports = ImmutableSet.of()
    Map<String, String> env = ImmutableMap.of()
    Map<Object, String> volumes = ImmutableMap.of()
    boolean daemonize = true
    boolean clean = false

    DockerRunTask() {
        super(DockerRunTask.class)
    }

    def command(String[] command){
        this.command = Arrays.asList(command)
    }
}
