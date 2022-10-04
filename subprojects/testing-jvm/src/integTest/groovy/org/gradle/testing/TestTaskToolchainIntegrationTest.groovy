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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getDifferentVersion
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJvmInstallationMetadata

class TestTaskToolchainIntegrationTest extends AbstractIntegrationSpec {

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

        file("src/test/java/ToolchainTest.java") << """
            import org.junit.*;

            public class ToolchainTest {
               @Test
               public void test() {
                  System.out.println("Tests running with " + System.getProperty("java.home"));
                  Assert.assertEquals(1,1);
               }
            }
        """

        // Compile with the minimum version to make sure the runtime can execute the compiled class
        def compileWithVersion = [jdkMetadataCurrent, jdkMetadata1, jdkMetadata2].collect {
            it.languageVersion.majorVersion.toInteger()
        }.min()

        buildFile << """
            apply plugin: "java"

            ${mavenCentralRepository()}

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            tasks.withType(JavaCompile).configureEach {
                javaCompiler = javaToolchains.compilerFor {
                    languageVersion = JavaLanguageVersion.of(${compileWithVersion})
                }
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
        withInstallations(jdkMetadataCurrent, jdkMetadata1, jdkMetadata2).run(":test", "--info")

        then:
        executedAndNotSkipped(":test")
        outputContains("Tests running with ${targetJdk.javaHome.toAbsolutePath()}")

        where:
        what             | when                                 | withTool | withExecutable | withJavaExtension
        "current JVM"    | "when toolchains are not configured" | false    | false          | false
        "java extension" | "when configured"                    | false    | false          | true
        "executable"     | "when configured"                    | false    | true           | false
        "assigned tool"  | "when configured"                    | true     | false          | false
        "executable"     | "over java extension"                | false    | true           | true
        "assigned tool"  | "over everything else"               | true     | true           | true
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
            test {
                executable = "${TextUtil.normaliseFileSeparators(jdk.javaHome.toString() + "/bin/java")}"
            }
        """
    }

    private TestFile configureLauncher(JvmInstallationMetadata jdk) {
        buildFile << """
            test {
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

}
