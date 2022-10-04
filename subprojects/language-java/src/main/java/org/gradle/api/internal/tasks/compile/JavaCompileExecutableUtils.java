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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.SpecificInstallationToolchainSpec;

import javax.annotation.Nullable;
import java.io.File;

public class JavaCompileExecutableUtils {

    @Nullable
    public static JavaToolchainSpec getExecutableOverrideToolchainSpec(JavaCompile task, ObjectFactory objectFactory) {
        ForkOptions forkOptions = task.getOptions().getForkOptions();
        File customJavaHome = forkOptions.getJavaHome();
        if (customJavaHome != null) {
            return new SpecificInstallationToolchainSpec(objectFactory, customJavaHome);
        }

        String customExecutable = forkOptions.getExecutable();
        if (customExecutable != null) {
            File executable = new File(customExecutable);
            if (executable.exists()) {
                // Relying on the layout of the toolchain distribution: <JAVA HOME>/bin/<executable>
                File parentJavaHome = executable.getParentFile().getParentFile();
                return new SpecificInstallationToolchainSpec(objectFactory, parentJavaHome);
            } else {
                throw new InvalidUserDataException("The configured executable does not exist (" + executable.getAbsolutePath() + ")");
            }
        }

        return null;
    }
}
