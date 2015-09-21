Docker Gradle Plugin
====================
Adds basic tasks for building and pushing docker images based on a simple
configuration block that specifies the container name, the Dockerfile, task
dependencies, and any additional file resources required for the Docker build.

Usage
-----
Apply the plugin using standard gradle convention:

    plugins {
        id 'com.palantir.docker'
    }

Set the container name, and then optionally specify a Dockerfile path, any task
dependencies and file resources required for the Docker build. This plugin will
automatically include outputs of task dependencies in the Docker build context.

**Examples**

Simplest configuration:

    docker {
        name 'hub.docker.com/username/my-app:version'
    }

Configuration specifying all parameters:

    docker {
        name 'hub.docker.com/username/my-app:version'
        dockerfile 'Dockerfile'
        dependsOn tasks.distTar
        files 'file1.txt', 'file2.txt'
    }

To build a docker container, run the `docker` task. To push that container to a
docker repository, run the `dockerPush` task.

Tasks
-----

 * `docker`: build a docker container with the specified name and Dockerfile
 * `dockerPush`: push the specified container to a docker repository
 * `dockerPrepare`: prepare to build a docker container by copying
   dependent task outputs, referenced files, and `dockerfile` into a temporary 
   directory
 * `dockerClean`: remove temporary directory associated with the docker build

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
