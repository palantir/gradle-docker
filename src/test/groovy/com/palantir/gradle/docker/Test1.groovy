package com.palantir.gradle.docker

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/**
 * (С) RGS Group, http://www.rgs.ru
 *
 * @author Nikolay Minyashkin (nikolay_minyashkin@rgs.ru) Created on 8/1/17.
 */
class Test1 extends AbstractPluginTest {
    def 'can link containers'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.docker-run'
            }

            task stopLinkedContainers {
                doLast {
                    "docker rm -f bar-linked-container-1".execute().waitForProcessOutput()
                    "docker rm -f bar-linked-container-2".execute().waitForProcessOutput()
                }
            }

            task runLinkedContainers {
                doLast {
                    "docker run -d --name bar-linked-container-1 alpine:3.2 tail -f /dev/null".execute().waitForProcessOutput()
                    "docker run -d --name bar-linked-container-2 alpine:3.2 tail -f /dev/null".execute().waitForProcessOutput()
                }
            }
                      
            dockerRun {
                name 'bar-linking-test'
                image 'alpine:3.2'
                ports '8080'
//                command 'ping', 'linked1', '-w1', '&&', 'echo', "Hello world"
                command '/bin/sh', '-c', 'ping linked1 -w1 && ping linked2 -w1'
                links 'bar-linked-container:linked1', 'bar-linked-container-2:linked2'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('dockerRemoveContainer', 'stopLinkedContainers', 'runLinkedContainers', 'dockerRun', 'dockerRunStatus').build()

        then:
        buildResult.task(':dockerRemoveContainer').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRun').outcome == TaskOutcome.SUCCESS

        buildResult.task(':dockerRunStatus').outcome == TaskOutcome.SUCCESS
//        buildResult.output =~ /(?m):dockerRunStatus\nDocker container 'bar-linking-test' is STOPPED./
        buildResult.output =~ /(?m):\.*0% packet loss\.*/
    }

}
