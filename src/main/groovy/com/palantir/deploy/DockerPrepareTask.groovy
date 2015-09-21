/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.deploy

import java.util.regex.Pattern

import org.gradle.api.Task
import org.gradle.api.tasks.Copy

class DockerPrepareTask extends Copy {

    public void configure() {
        def ext = project.extensions.findByType(DockerExtension.class)

        from (ext.resolvedDockerfile) {
            rename { fileName ->
                fileName.replace(ext.resolvedDockerfile.getName(), 'Dockerfile')
            }
        }

        for (Task depTask : ext.dependencies) {
            from depTask.outputs
        }

        for (String file : ext.resolvedFiles) {
            from file
        }

        into "${project.buildDir}/docker"
    }

}
