Docker Gradle Plugin
====================
[![Build Status](https://travis-ci.org/palantir/gradle-docker.svg?branch=develop)](https://travis-ci.org/palantir/gradle-docker)

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
        dockerComposeTemplate 'my-template.yml'
        dockerComposeFile 'my-docker-compose.yml'
    }

To build a docker container, run the `docker` task. To push that container to a
docker repository, run the `dockerPush` task.


Generating docker-compose dependencies
--------------------------------------

The plugin provides mechanisms for managing dependencies between docker images
used in orchestrating `docker-compose` environments. Each project can declare
its dependencies on other docker images and publish an artifact advertising those
dependencies. The plugin uses standard Maven/Ivy machanism for declaring and
resolving dependencies.

The `generateDockerCompose` task generates a `docker-compose.yml` file from
a user-defined template by replacing each version variable by the concrete version
declared by the transitive dependencies of the docker configuration.

### Specifying dependencies on Docker images
The plugin adds a `docker` Gradle component and a `docker` Gradle configuration that
can be used to specify and publish dependencies on other Docker containers.
The `generateDockerCompose` task (see below) uses the transitive dependencies
of the `docker` configuration to populate a `docker-compose.yml.template` file
with the image versions specified by this project and all its transitive
dependencies.

    dependencies {
        docker 'foogroup:barmodule:0.1.2'
        docker project(":someSubProject")
    }

    publishing {
        publications {
            dockerPublication(MavenPublication) {
                from components.docker
                artifactId project.name + "-docker"
            }
        }
    }

The above configuration adds a Maven publication that specifies dependencies
on `barmodule` and the `someSubProject` Gradle sub project. The resulting POM
file has two `dependency` entries, one for each dependency.

### Generating docker-compose.yml files from templates
The `generateDockerCompose` task performs two operations: First, it generates
a mapping `group:name --> version` from the dependencies of the `docker`
configuration (see above). Second, it replaces all occurrences of version
variables of the form `{{group:name}}` in the `docker-compose.yml.template` file
by the resolved versions and writes the resulting file as `docker-compose.yml`.

**Example**

Assume a `docker-compose.yml.template` as follows:

    myservice:
      image: 'repository/myservice:latest'
    otherservice:
      image: 'repository/otherservice:{{othergroup:otherservice}}'

The `build.gradle` of this project publishes a docker Maven artifact declaring
a dependency on a docker image published as 'othergroup:otherservice' in version
0.1.2:

    plugins {
        id 'maven-publish'
        id 'com.palantir.docker'
    }

    docker {
        name 'foo'
    }

    group 'mygroup'
    name 'myservice'
    version '2.3.4'

    dependencies {
        docker 'othergroup:otherservice:0.1.2'
    }

    publishing {
        publications {
            dockerPublication(MavenPublication) {
                from components.docker
                artifactId project.name + "-docker"
            }
        }
    }

The `generateDockerCompose` task creates a `docker-compose.yml` as follows:

    myservice:
      image: 'repository/myservice:latest'
    otherservice:
      image: 'repository/otherservice:0.1.2'

The `generateDockerCompose` task fails if the template file contains variables that
cannot get resolved using the provided `docker` dependencies. Version conflicts between
transitive dependencies of the same artifact are handled with the standard Gradle
semantics: each artifact is resolved to the highest declared version.

Tasks
-----

 * `docker`: build a docker container with the specified name and Dockerfile
 * `dockerPush`: push the specified container to a docker repository
 * `dockerPrepare`: prepare to build a docker container by copying
   dependent task outputs, referenced files, and `dockerfile` into a temporary
   directory
 * `dockerClean`: remove temporary directory associated with the docker build
 * `dockerfileZip`: builds a ZIP file containing the configured Dockerfile
 * `generateDockerCompose`: Populates a docker-compose file template with image
   versions declared by dependencies



License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
