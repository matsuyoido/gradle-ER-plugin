package com.matsuyoido.plugin.er;

import com.matsuyoido.plugin.er.task.YamlDDLTask;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;

public class MainPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getExtensions().create("yamlER", RootExtension.class, project);
        project.afterEvaluate(this::setupTask);
    }

    void setupTask(Project project) {
        TaskContainer taskContainer = project.getTasks();
        RootExtension extension = project.getExtensions()
                                         .getByType(RootExtension.class);

        if (!extension.getDDLConfig().isEmpty()) {
            YamlDDLTask task = taskContainer.create("ddl", YamlDDLTask.class);
            // always run
            task.getOutputs().upToDateWhen(t -> false);
            // group
            task.setGroup("database");
        }
    }

}
