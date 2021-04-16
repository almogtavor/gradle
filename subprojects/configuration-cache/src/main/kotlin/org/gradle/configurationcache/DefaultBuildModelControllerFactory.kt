/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.GradleInternal
import org.gradle.configuration.ProjectsPreparer
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.build.BuildModelController
import org.gradle.initialization.BuildModelControllerFactory
import org.gradle.initialization.ConfigurationCache
import org.gradle.initialization.ConfigurationCacheAwareBuildModelController
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.initialization.VintageBuildModelController


class DefaultBuildModelControllerFactory(
    val startParameter: ConfigurationCacheStartParameter,
    val projectsPreparer: ProjectsPreparer,
    val settingsPreparer: SettingsPreparer,
    val taskExecutionPreparer: TaskExecutionPreparer,
    val configurationCache: ConfigurationCache
) : BuildModelControllerFactory {
    override fun create(gradle: GradleInternal): BuildModelController {
        val vintageController = VintageBuildModelController(gradle, projectsPreparer, settingsPreparer, taskExecutionPreparer)
        return if (startParameter.isEnabled) {
            ConfigurationCacheAwareBuildModelController(gradle, vintageController, configurationCache)
        } else {
            vintageController
        }
    }
}