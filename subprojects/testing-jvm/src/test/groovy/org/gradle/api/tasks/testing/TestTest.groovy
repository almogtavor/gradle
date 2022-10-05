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

package org.gradle.api.tasks.testing


import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.internal.TextUtil

class TestTest extends AbstractProjectBuilderSpec {

    def setup() {
        def toolchainService = Mock(JavaToolchainService)
        project.extensions.add("javaToolchains", toolchainService)
    }

    def "uses current JVM toolchain launcher as convention"() {
        def task = project.tasks.create("test", Test)
        task.testClassesDirs = TestFiles.fixed(new File("tmp"))
        task.binaryResultsDirectory.fileValue(new File("out"))
        def javaHome = Jvm.current().javaHome

        when:
        def spec = task.createJvmTestExecutionSpec()
        def actualLauncher = task.javaLauncher.get()

        then:
        spec.javaForkOptions.executable == TextUtil.normaliseFileSeparators(new File(javaHome, "/bin/java").absolutePath)
        actualLauncher.metadata.installationPath.toString() == javaHome.toString()
    }

    def "uses toolchain launcher over custom executable"() {
        def task = project.tasks.create("test", Test)
        task.testClassesDirs = TestFiles.fixed(new File("tmp"))
        task.binaryResultsDirectory.fileValue(new File("out"))
        def launcher = Mock(JavaLauncher)

        def toolchainExecutable = Mock(RegularFile)
        toolchainExecutable.toString() >> "/test/toolchain/bin/java"
        launcher.executablePath >> toolchainExecutable

        given:
        task.javaLauncher.set(launcher)
        task.executable = "/test/custom/executable/java"

        when:
        def spec = task.createJvmTestExecutionSpec()

        then:
        spec.javaForkOptions.executable == "/test/toolchain/bin/java"
    }

    def 'fails if custom executable does not exist'() {
        def testTask = project.tasks.create("test", Test)
        def invalidJava = "invalidjava"

        when:
        testTask.executable = invalidJava
        testTask.createJvmTestExecutionSpec()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        e.message.contains("Failed to query the value of task ':test' property 'javaLauncher'")
        def cause = e.cause
        cause.message.contains("The configured executable does not exist")
        cause.message.contains(invalidJava)
    }
}
