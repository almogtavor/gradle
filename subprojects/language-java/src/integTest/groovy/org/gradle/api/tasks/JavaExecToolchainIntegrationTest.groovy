/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import spock.lang.IgnoreIf

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getDifferentVersion
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJvmInstallationMetadata

class JavaExecToolchainIntegrationTest extends AbstractIntegrationSpec {

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "can manually set java launcher via  #type toolchain on java exec task #jdk"() {
        buildFile << """
            plugins {
                id 'java'
                id 'application'
            }

            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }

            run {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
            }

            application {
                mainClass = 'App'
            }
        """

        file('src/main/java/App.java') << testApp()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("run")
            .run()

        then:
        outputContains("App running with ${jdk.javaHome.absolutePath}")
        noExceptionThrown()

        where:
        type           | jdk
        'differentJdk' | AvailableJavaHomes.differentJdk
        'current'      | Jvm.current()
    }

    def "can set java launcher via #type toolchain on manually created java exec task to #jdk with #plugin"() {
        buildFile << """
            plugins {
                id '${plugin}'
            }

            tasks.register("run", JavaExec) {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
                }
                setJvmArgs(['-version'])
                mainClass = 'None'
            }
        """

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + jdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("run")
            .run()

        then:
        outputContains("Command: ${jdk.javaHome.absolutePath}")
        noExceptionThrown()

        where:
        type           | jdk                             | plugin

        'differentJdk' | AvailableJavaHomes.differentJdk | 'java-base'
        'current'      | Jvm.current()                   | 'java-base'

        'differentJdk' | AvailableJavaHomes.differentJdk | 'jvm-toolchains'
        'current'      | Jvm.current()                   | 'jvm-toolchains'
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "JavaExec task is configured using default toolchain"() {
        def someJdk = AvailableJavaHomes.differentJdk
        buildFile << """
            plugins {
                id 'java'
                id 'application'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${someJdk.javaVersion.majorVersion})
                }
            }

            application {
                mainClass = 'App'
            }
        """

        file('src/main/java/App.java') << testApp()

        when:
        result = executer
            .withArgument("-Porg.gradle.java.installations.paths=" + someJdk.javaHome.absolutePath)
            .withArgument("--info")
            .withTasks("run")
            .run()

        then:
        outputContains("App running with ${someJdk.javaHome.absolutePath}")
        noExceptionThrown()
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

        file("src/main/java/Main.java") << """
            package org.example;
            import java.lang.System;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("App running with " + System.getProperty("java.home"));
                }
            }
        """

        // Compile with the minimum version to make sure the runtime can execute the compiled class
        def compileWithVersion = [jdkMetadataCurrent, jdkMetadata1, jdkMetadata2].collect {
            it.languageVersion.majorVersion.toInteger()
        }.min()

        buildFile << """
            apply plugin: "application"

            compileJava {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${compileWithVersion})
                }
            }

            application {
                mainClass = "org.example.Main"
            }
        """

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureLauncher(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }
        if (withJavaExtension) {
            configureJavaExtension(useJdk())
        }

        when:
        withInstallations(jdkMetadataCurrent, jdkMetadata1, jdkMetadata2).run(":run")

        then:
        executedAndNotSkipped(":run")
        outputContains("App running with ${targetJdk.javaHome.toAbsolutePath()}")

        where:
        what             | when                                 | withTool | withExecutable | withJavaExtension
        "current JVM"    | "when toolchains are not configured" | false    | false          | false
        "java extension" | "when configured"                    | false    | false          | true
        "executable"     | "when configured"                    | false    | true           | false
        "assigned tool"  | "when configured"                    | true     | false          | false
        "executable"     | "over java extension"                | false    | true           | true
        "assigned tool"  | "over everything else"               | true     | true           | true
    }

    def "uses #what toolchain #when (without application plugin)"() {
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

        buildFile << """
            plugins {
                id 'jvm-toolchains'
            }

            task run(type: JavaExec) {
                setJvmArgs(['-version'])
                mainClass = 'None'
            }
        """

        // Order of if's is important as it denotes toolchain priority
        if (withTool) {
            configureLauncher(useJdk())
        }
        if (withExecutable) {
            configureExecutable(useJdk())
        }

        when:
        withInstallations(jdkMetadataCurrent, jdkMetadata1, jdkMetadata2).run(":run", "--info")

        then:
        executedAndNotSkipped(":run")
        outputContains("Command: ${targetJdk.javaHome.toAbsolutePath()}")

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
            run {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javaHome.toString() + "/bin/java")}"
            }
        """
    }

    private TestFile configureLauncher(JvmInstallationMetadata jdk) {
        buildFile << """
            run {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${jdk.languageVersion.majorVersion})
                }
            }
        """
    }

    private withInstallations(JvmInstallationMetadata... jdkMetadata) {
        def installationPaths = jdkMetadata.collect { it.javaHome.toAbsolutePath().toString() }.join(",")
        executer
            .withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        this
    }

    private static String testApp() {
        return """
            public class App {
               public static void main(String[] args) {
                 System.out.println("App running with " + System.getProperty("java.home"));
               }
            }
        """.stripIndent()
    }

}
