/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getDifferentVersion
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJvmInstallationMetadata

class JavadocToolchainIntegrationTest extends AbstractIntegrationSpec {

    def "changing toolchain invalidates task"() {
        def jdkMetadata1 = getJvmInstallationMetadata(Jvm.current())
        def jdkMetadata2 = getJvmInstallationMetadata(differentVersion)

        buildFile << """
            plugins {
                id 'java'
            }

            javadoc {
                javadocTool = javaToolchains.javadocToolFor {
                    def version = ${jdkMetadata1.languageVersion.majorVersion}
                    version = providers.gradleProperty('test.javadoc.version').getOrElse(version)
                    languageVersion = JavaLanguageVersion.of(version)
                }
            }
        """
        file('src/main/java/Lib.java') << testLib()

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        executedAndNotSkipped(":javadoc")
        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")

        when:
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        skipped(":javadoc")

        when:
        executer.withArgument("-Ptest.javadoc.version=${jdkMetadata2.languageVersion.majorVersion}")
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        executedAndNotSkipped(":javadoc")
        file("build/docs/javadoc/Lib.html").text.contains("Some API documentation.")

        when:
        executer.withArgument("-Ptest.javadoc.version=${jdkMetadata2.languageVersion.majorVersion}")
        withInstallations(jdkMetadata1, jdkMetadata2).run(":javadoc", "--info")
        then:
        skipped(":javadoc")
    }

    def "uses #what toolchain #when"() {
        JvmInstallationMetadata jdkMetadataCurrent = getJvmInstallationMetadata(Jvm.current())
        JvmInstallationMetadata jdkMetadata1 = getJvmInstallationMetadata(differentVersion)
        JvmInstallationMetadata jdkMetadata2 = getJvmInstallationMetadata(getDifferentVersion { it.languageVersion != jdkMetadata1.languageVersion })

        // When at least one toolchain is used for configuration, expect the first toolchain to be the target.
        // Otherwise, expect the current toolchain as a fallback
        JvmInstallationMetadata targetJdk = jdkMetadataCurrent
        def useJdk = {
            if (targetJdk === jdkMetadataCurrent) {
                targetJdk = jdkMetadata1
                return jdkMetadata1
            } else {
                return jdkMetadata2
            }
        }

        file('src/main/java/Lib.java') << testLib()

        buildFile << """
            plugins {
                id 'java'
            }

            javadoc {
                options.jFlags("-version")
            }
        """

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureJavadocTool(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }
        if (withJavaExtension) {
            configureJavaExtension(useJdk())
        }

        when:
        withInstallations(jdkMetadataCurrent, jdkMetadata1, jdkMetadata2).run(":javadoc")

        then:
        executedAndNotSkipped(":javadoc")
        errorOutput.contains(targetJdk.javaVersion)

        where:
        what             | when                                 | withTool | withExecutable | withJavaExtension
        "current JVM"    | "when toolchains are not configured" | false    | false          | false
        "java extension" | "when configured"                    | false    | false          | true
        "executable"     | "when configured"                    | false    | true           | false
        "assigned tool"  | "when configured"                    | true     | false          | false
        "executable"     | "over java extension"                | false    | true           | true
        "assigned tool"  | "over everything else"               | true     | true           | true
    }

    def "uses #what toolchain #when (without java plugin)"() {
        JvmInstallationMetadata jdkMetadataCurrent = getJvmInstallationMetadata(Jvm.current())
        JvmInstallationMetadata jdkMetadata1 = getJvmInstallationMetadata(differentVersion)
        JvmInstallationMetadata jdkMetadata2 = getJvmInstallationMetadata(getDifferentVersion { it.languageVersion != jdkMetadata1.languageVersion })

        // When at least one toolchain is used for configuration, expect the first toolchain to be the target.
        // Otherwise, expect the current toolchain as a fallback
        JvmInstallationMetadata targetJdk = jdkMetadataCurrent
        def useJdk = {
            if (targetJdk === jdkMetadataCurrent) {
                targetJdk = jdkMetadata1
                return jdkMetadata1
            } else {
                return jdkMetadata2
            }
        }

        file('src/main/java/Lib.java') << testLib()

        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            task javadoc(type: Javadoc) {
                source = project.layout.files("src/main/java")
                destinationDir = project.layout.buildDirectory.dir("docs/javadoc").get().getAsFile()
            }

            javadoc {
                options.jFlags("-version")
            }
        """

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureJavadocTool(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }

        when:
        withInstallations(jdkMetadataCurrent, jdkMetadata1, jdkMetadata2).run(":javadoc")

        then:
        executedAndNotSkipped(":javadoc")
        errorOutput.contains(targetJdk.javaVersion)

        where:
        what            | when                                 | withTool | withExecutable
        "current JVM"   | "when toolchains are not configured" | false    | false
        "executable"    | "when configured"                    | false    | true
        "assigned tool" | "when configured"                    | true     | false
        "assigned tool" | "over everything else"               | true     | true
    }

    private TestFile configureJavaExtension(JvmInstallationMetadata jdk) {
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.languageVersion.majorVersion})
                }
            }
        """
    }

    private TestFile configureExecutable(JvmInstallationMetadata jdk) {
        buildFile << """
            javadoc {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javaHome.toString() + "/bin/javadoc")}"
            }
        """
    }

    private TestFile configureJavadocTool(JvmInstallationMetadata jdk) {
        buildFile << """
            javadoc {
                javadocTool = javaToolchains.javadocToolFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.languageVersion.majorVersion})
                }
            }
        """
    }

    private withInstallations(JvmInstallationMetadata... jdkMetadata) {
        def installationPaths = jdkMetadata.collect { it.javaHome.toAbsolutePath().toString() }.join(",")
        executer.withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this
    }

    private static String testLib() {
        return """
            public class Lib {
               /**
                * Some API documentation.
                */
               public void foo() {
               }
            }
        """.stripIndent()
    }
}
