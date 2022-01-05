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

package org.gradle.configurationcache.isolated;

import org.gradle.configurationcache.fixtures.SomeToolingModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.util.ArrayList;
import java.util.List;

public class FetchCustomModelForEachProjectInTree implements BuildAction<List<SomeToolingModel>> {
    @Override
    public List<SomeToolingModel> execute(BuildController controller) {
        List<SomeToolingModel> result = new ArrayList<>();
        GradleBuild buildModel = controller.getBuildModel();
        collectModelsForProjects(controller, result, buildModel);
        for (GradleBuild build : buildModel.getEditableBuilds()) {
            collectModelsForProjects(controller, result, build);
        }
        return result;
    }

    private void collectModelsForProjects(BuildController controller, List<SomeToolingModel> result, GradleBuild buildModel) {
        for (BasicGradleProject project : buildModel.getProjects()) {
            SomeToolingModel model = controller.findModel(project, SomeToolingModel.class);
            if (model != null) {
                result.add(model);
            }
        }
    }
}
