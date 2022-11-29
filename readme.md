<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-docker"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

Docker Gradle Plugin
====================
[![Build Status](https://circleci.com/gh/palantir/gradle-docker.svg?style=shield)](https://circleci.com/gh/palantir/gradle-docker)
[![Gradle Plugins Release](https://img.shields.io/github/release/palantir/gradle-docker.svg)](https://plugins.gradle.org/plugin/com.palantir.docker)

Disclaimer: This Repo is now Defunct
-------------

- This repo is on life support only - although we will keep it working, no new features are accepted;
- It is no longer used internally at Palantir.

Docker Plugin
-------------

This repository provides three Gradle plugins for working with Docker containers:
- `com.palantir.docker`: add basic tasks for building and pushing
  docker images based on a simple configuration block that specifies the container
  name, the Dockerfile, task dependencies, and any additional file resources
  required for the Docker build.
- `com.palantir.docker-compose`: adds a task for populating placeholders in a
  docker-compose template file with image versions resolved from
  dependencies.
- `com.palantir.docker-run`: adds tasks for starting, stopping, statusing and cleaning
  up a named container based on a specified image

Apply the plugin using standard gradle convention:

````gradle
plugins {
    id 'com.palantir.docker' version '<version>'
}
````

Set the container name, and then optionally specify a Dockerfile, any task
dependencies and file resources required for the Docker build. This plugin will
automatically include outputs of task dependencies in the Docker build context.

**Docker Configuration Parameters**
- `name` the name to use for this container, may include a tag
- `tags` (deprecated) (optional) an argument list of tags to create; any tag in `name` will
  be stripped before applying a specific tag; defaults to the empty set
- `tag` (optional) a tag to create with a specified task name
- `dockerfile` (optional) the dockerfile to use for building the image; defaults to
  `project.file('Dockerfile')` and must be a file object
- `files` (optional) an argument list of files to be included in the Docker build context, evaluated per `Project#files`. For example, `files tasks.distTar.outputs` adds the TAR/TGZ file produced by the `distTar` tasks, and `files tasks.distTar.outputs, 'my-file.txt'` adds the archive in addition to file `my-file.txt` from the project root directory. The specified files are collected in a Gradle CopySpec which may be copied `into` the Docker build context directory. The underlying CopySpec may also be used to copy entire directories into the build context. The following example adds the aforementioned archive and text file to the CopySpec, uses the CopySpec to add all files `from` `src/myDir` into the CopySpec, then finally executes the copy into the directory `myDir` in docker build context.
````gradle
docker {
    files tasks.distTar.outputs, 'my-file.txt'
    copySpec.from("src/myDir").into("myDir")
}
````
The final structure will be:
```
build/
  docker/
    myDir/
      my-file.txt
      // contents of task.distTar.outputs
      // files from src/myDir
```
- `buildArgs` (optional) an argument map of string to string which will set --build-arg
  arguments to the docker build command; defaults to empty, which results in no --build-arg parameters
- `labels` (optional) a map of string to string which will set --label arguments
  to the docker build command; defaults to empty, which results in no labels applied.
- `pull` (optional) a boolean argument which defines whether Docker should attempt to pull
  a newer version of the base image before building; defaults to `false`
- `noCache` (optional) a boolean argument which defines whether Docker build should add the option --no-cache,
    so that it rebuilds the whole image from scratch; defaults to `false`
- `buildx` (optional) a boolean argument which defines whether Docker build should use buildx for cross platform builds; defaults to `false`
- `platform` (optional) a list of strings argument which defines which platforms buildx should target; defaults to empty
- `builder` (optional) a string argument which defines which builder buildx should use; defaults to `null`
- `load` (optional) a boolean argument which defines whether Docker buildx builder should add --load flag,
  loading the image into the local repository; defaults to `false`
- `push` (optional) a boolean argument which defines whether Docker buildx builder should add --push flag,
  pushing the image into the remote registry; defaults to `false`

To build a docker container, run the `docker` task. To push that container to a
docker repository, run the `dockerPush` task.

Tag and Push tasks for each tag will be generated for each provided `tag` and `tags` entry.

**Examples**

Simplest configuration:

```gradle
docker {
    name 'hub.docker.com/username/my-app:version'
}
```

Canonical configuration for building a Docker image from a distribution archive:

```gradle
// Assumes that Gradle "distribution" plugin is applied
docker {
    name 'hub.docker.com/username/my-app:version'
    files tasks.distTar.outputs   // adds resulting *.tgz to the build context
}
```

Configuration specifying all parameters:

```gradle
docker {
    name 'hub.docker.com/username/my-app:version'
    tags 'latest' // deprecated, use 'tag'
    tag 'myRegistry', 'my.registry.com/username/my-app:version'
    dockerfile file('Dockerfile')
    files tasks.distTar.outputs, 'file1.txt', 'file2.txt'
    buildArgs([BUILD_VERSION: 'version'])
    labels(['key': 'value'])
    pull true
    noCache true
}
```


Managing Docker image dependencies
----------------------------------
The `com.palantir.docker` and `com.palantir.docker-compose` plugins provide
functionality to declare and resolve version-aware dependencies between docker
images. The primary use-case is to generate `docker-compose.yml` files whose
image versions are mutually compatible and up-to-date in cases where multiple
images depend on the existence of the same Dockerized service.

### Specifying and publishing dependencies on Docker images

The `docker` plugin adds a `docker` Gradle component and a `docker` Gradle
configuration that can be used to specify and publish dependencies on other
Docker containers.

**Example**

```gradle
plugins {
    id 'maven-publish'
    id 'com.palantir.docker'
}

...

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
```

The above configuration adds a Maven publication that specifies dependencies on
`barmodule` and the `someSubProject` Gradle sub project. The resulting POM file
has two `dependency` entries, one for each dependency. Each project can declare
its dependencies on other docker images and publish an artifact advertising
those dependencies.

### Generating docker-compose.yml files from dependencies

The `com.palantir.docker-compose` plugin uses the transitive dependencies of the
`docker` configuration to populate a `docker-compose.yml.template` file with the
image versions specified by this project and all its transitive dependencies.
The plugin uses standard Maven/Ivy machanism for declaring and resolving
dependencies.

The `generateDockerCompose` task generates a `docker-compose.yml` file from a
user-defined template by replacing each version variable by the concrete version
declared by the transitive dependencies of the docker configuration.  The task
performs two operations: First, it generates a mapping `group:name --> version`
from the dependencies of the `docker` configuration (see above). Second, it
replaces all occurrences of version variables of the form `{{group:name}}` in
the `docker-compose.yml.template` file by the resolved versions and writes the
resulting file as `docker-compose.yml`.

The `docker-compose` plugin also provides a `dockerComposeUp` task that starts
the docker images specified in the `dockerComposeFile` in detached mode.
You can also use the `dockerComposeDown` task to stop the containers.


**Example**

Assume a `docker-compose.yml.template` as follows:

```yaml
myservice:
  image: 'repository/myservice:latest'
otherservice:
  image: 'repository/otherservice:{{othergroup:otherservice}}'
```

`build.gradle` declares a dependency on a docker image published as
'othergroup:otherservice' in version 0.1.2:

```gradle
plugins {
    id 'com.palantir.docker-compose'
}

dependencies {
    docker 'othergroup:otherservice:0.1.2'
}
```

The `generateDockerCompose` task creates a `docker-compose.yml` as follows:

```yaml
myservice:
  image: 'repository/myservice:latest'
otherservice:
  image: 'repository/otherservice:0.1.2'
```

The `generateDockerCompose` task fails if the template file contains variables
that cannot get resolved using the provided `docker` dependencies. Version
conflicts between transitive dependencies of the same artifact are handled with
the standard Gradle semantics: each artifact is resolved to the highest declared
version.

**Configuring file locations**

The template and generated file locations are customizable through the
`dockerCompose` extension:

```gradle
dockerCompose {
    template 'my-template.yml'
    dockerComposeFile 'my-docker-compose.yml'
}
```

Docker Run Plugin
-----------------
Apply the plugin using standard gradle convention:

```gradle
plugins {
    id 'com.palantir.docker-run' version '<version>'
}
```

Use the `dockerRun` configuration block to configure the name, image and optional
command to execute for the `dockerRun` tasks:

```gradle
dockerRun {
    name 'my-container'
    image 'busybox'
    volumes 'hostvolume': '/containervolume'
    ports '7080:5000'
    daemonize true
    env 'MYVAR1': 'MYVALUE1', 'MYVAR2': 'MYVALUE2'
    command 'sleep', '100'
    arguments '--hostname=custom', '-P'
}
```

**Docker Run Configuration Parameters**
- `name` the name to use for this container, may include a tag.
- `image` the name of the image to use.
- `volumes` optional map of volumes to mount in the container. The key is the path
  to the host volume, resolved using [`project.file()`](https://docs.gradle.org/current/userguide/working_with_files.html#sec:locating_files).
  The value is the exposed container volume path.
- `ports` optional mapping `local:container` of local port to container port.
- `env` optional map of environment variables to supply to the running container.
  These must be exposed in the Dockerfile with `ENV` instructions.
- `daemonize` defaults to true to daemonize the container after starting. However
  if your container runs a command and exits, you can set this to false.
- `ignoreExitValue` (optional) to ignore the exit code returned from the execution of the docker command; defaults to `false`
- `clean` (optional) a boolean argument which adds `--rm` to the `docker run`
  command to ensure that containers are cleaned up after running; defaults to `false`
- `command` the command to run.
- `arguments` additional arguments to be passed into the docker run command.
   Please see https://docs.docker.com/engine/reference/run/ for possible values.
   
Tasks
-----

 * **Docker**
   * `docker`: build a docker image with the specified name and Dockerfile
   * `dockerTag`: tag the docker image with all specified tags
   * `dockerTag<tag>`: tag the docker image with `<tag>`
   * `dockerPush`: push the specified image to a docker repository
   * `dockerPush<tag>`: push the `<tag>` docker image to a docker repository
   * `dockerTagsPush`: push all tagged docker images to a docker repository
   * `dockerPrepare`: prepare to build a docker image by copying
     dependent task outputs, referenced files, and `dockerfile` into a temporary
     directory
   * `dockerClean`: remove temporary directory associated with the docker build
   * `dockerfileZip`: builds a ZIP file containing the configured Dockerfile
 * **Docker Compose**
   * `generateDockerCompose`: Populates a docker-compose file template with image
     versions declared by dependencies
   * `dockerComposeUp`: Brings up services defined in `dockerComposeFile` in
     detacted state
   * `dockerComposeDown`: Stops services defined in `dockerComposeFile`
 * **Docker Run**
   * `dockerRun`: run the specified image with the specified name
   * `dockerStop`: stop the running container
   * `dockerRunStatus`: indicate the run status of the container
   * `dockerRemoveContainer`: remove the container

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).

Contributing
------------
Contributions to this project must follow the [contribution guide](CONTRIBUTING.md).
