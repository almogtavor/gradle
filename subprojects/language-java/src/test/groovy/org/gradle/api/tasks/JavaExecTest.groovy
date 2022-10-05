/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks

import org.gradle.api.file.RegularFile
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.internal.TextUtil

class JavaExecTest extends AbstractProjectBuilderSpec {

    def setup() {
        def toolchainService = Mock(JavaToolchainService)
        project.extensions.add("javaToolchains", toolchainService)
    }

    def "uses current JVM toolchain launcher as convention"() {
        def task = project.tasks.create('execJava', JavaExec)
        def javaHome = Jvm.current().javaHome

        when:
        def spec = task.createJavaExecAction()
        def actualLauncher = task.javaLauncher.get()

        then:
        spec.executable == TextUtil.normaliseFileSeparators(new File(javaHome, "/bin/java").absolutePath)
        actualLauncher.metadata.installationPath.toString() == javaHome.toString()
    }

    def "uses toolchain launcher over custom executable"() {
        def task = project.tasks.create('execJava', JavaExec)
        def launcher = Mock(JavaLauncher)

        def toolchainExecutable = Mock(RegularFile)
        toolchainExecutable.toString() >> "/test/toolchain/bin/java"
        launcher.executablePath >> toolchainExecutable

        given:
        task.javaLauncher.set(launcher)
        task.executable = "/test/custom/executable/java"

        when:
        def spec = task.createJavaExecAction()

        then:
        spec.executable == "/test/toolchain/bin/java"
    }
}
