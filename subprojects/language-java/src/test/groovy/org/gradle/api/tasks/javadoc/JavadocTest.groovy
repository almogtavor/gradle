/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks.javadoc

import org.apache.commons.io.FileUtils
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.api.tasks.javadoc.internal.JavadocToolAdapter
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import org.gradle.util.internal.TextUtil

class JavadocTest extends AbstractProjectBuilderSpec {

    def testDir = temporaryFolder.getTestDirectory()
    def destDir = new File(testDir, "dest")
    def srcDir = new File(testDir, "srcdir")
    def configurationMock = TestFiles.fixed(new File("classpath"))
    def tool = Mock(JavadocToolAdapter)

    Javadoc task

    def setup() {
        def toolchainService = Mock(JavaToolchainService)
        project.extensions.add("javaToolchains", toolchainService)

        task = TestUtil.createTask(Javadoc, project, "javadoc")
        task.setClasspath(configurationMock)
        task.setDestinationDir(destDir)
        task.source(srcDir)

        FileUtils.touch(new File(srcDir, "file.java"))

        tool.metadata >> Mock(JavaInstallationMetadata) {
            getLanguageVersion() >> JavaLanguageVersion.of(11)
        }
        tool.executablePath >> Mock(RegularFile) {
            toString() >> "/test/toolchain/bin/javadoc"
        }
    }

    def "execution uses the tool"() {
        task.getJavadocTool().set(tool)

        when:
        execute(task)

        then:
        1 * tool.execute(_)
    }

    def "execution with additional options uses the tool"() {
        task.getJavadocTool().set(tool)
        task.setMaxMemory("max-memory")
        task.setVerbose(true)

        when:
        execute(task)

        then:
        1 * tool.execute(_)
    }

    def "uses current JVM toolchain tool as convention"() {
        def javaHome = Jvm.current().javaHome

        when:
        def spec = task.createSpec(Mock(StandardJavadocDocletOptions))
        def actualTool = task.javadocTool.get()

        then:
        spec.executable == TextUtil.normaliseFileSeparators(new File(javaHome, "/bin/javadoc").absolutePath)
        actualTool.metadata.installationPath.toString() == javaHome.toString()
    }

    def "uses toolchain launcher over custom executable"() {
        task.javadocTool.set(tool)
        task.executable = "/test/custom/executable/java"

        when:
        def spec = task.createSpec(Mock(StandardJavadocDocletOptions))

        then:
        spec.executable == "/test/toolchain/bin/javadoc"
    }

    def "fails if custom executable does not exist"() {
        def invalidJavadoc = "invalidjavadoc"

        when:
        task.executable = invalidJavadoc
        task.createSpec(Mock(StandardJavadocDocletOptions))

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        e.message.contains("Failed to query the value of task ':javadoc' property 'javadocTool'")
        def cause = e.cause
        cause.message.contains("The configured executable does not exist")
        cause.message.contains(invalidJavadoc)
    }
}
