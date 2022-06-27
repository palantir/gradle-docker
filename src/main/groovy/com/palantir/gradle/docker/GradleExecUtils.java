/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.docker;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.util.TeeOutputStream;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

final class GradleExecUtils {
    public static void execWithErrorMessage(Project project, Action<ExecSpec> execSpecAction) {
        List<String> commandLine = new ArrayList<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ExecResult execResult = project.exec(execSpec -> {
            execSpecAction.execute(execSpec);
            execSpec.setIgnoreExitValue(true);
            execSpec.setStandardOutput(new TeeOutputStream(System.out, output));
            execSpec.setErrorOutput(new TeeOutputStream(System.err, output));
            commandLine.addAll(execSpec.getCommandLine());
        });

        if (execResult.getExitValue() != 0) {
            throw new GradleException(String.format(
                    "The command '%s' failed with exit code %d. Output:\n%s",
                    commandLine, execResult.getExitValue(), new String(output.toByteArray(), StandardCharsets.UTF_8)));
        }
    }

    private GradleExecUtils() {}
}
