/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.gradle.docker


import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class AbstractPluginTest extends Specification {

    @Rule
    TemporaryFolder tempFolder
    File projectDir
    File buildFile

    def setup() {
        projectDir = tempFolder.getRoot()
        buildFile = file('build.gradle')
        println("Build directory: \n" + projectDir.absolutePath)
    }

    GradleRunner with(String... tasks) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(tasks)
            .withPluginClasspath()
            .withDebug(true)
    }

    String exec(String task) {
        StringBuffer sout = new StringBuffer(), serr = new StringBuffer()
        Process proc = task.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()
        return sout.toString()
    }

    boolean execCond(String task) {
        StringBuffer sout = new StringBuffer(), serr = new StringBuffer()
        Process proc = task.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()
        return proc.exitValue() == 0
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected File createFile(String path, File baseDir = projectDir) {
        File file = file(path, baseDir)
        assert !file.exists()
        file.parentFile.mkdirs()
        assert file.createNewFile()
        return file
    }

    protected File file(String path, File baseDir = projectDir) {
        def splitted = path.split('/')
        def directory = splitted.size() > 1 ? directory(splitted[0..-2].join('/'), baseDir) : baseDir
        def file = new File(directory, splitted[-1])
        return file
    }

    protected File directory(String path, File baseDir = projectDir) {
        return new File(baseDir, path).with {
            mkdirs()
            return it
        }
    }

    protected int processCount() {
        StringBuffer sout = new StringBuffer(), serr = new StringBuffer()
        Process proc = 'docker ps -q'.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()
        assert proc.exitValue() == 0

        return sout.readLines().size()
    }

    protected String escapePath(String path) {
        return path.replaceAll('\\\\', '\\\\\\\\')
    }
}
