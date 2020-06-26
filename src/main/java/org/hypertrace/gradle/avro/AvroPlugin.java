package org.hypertrace.gradle.avro;

import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public class AvroPlugin implements Plugin<Project> {

  public static final String EXTENSION_NAME = "hypertraceAvro";
  public static final String COMPATIBILITY_CHECK_TASK_NAME = "avroCompatibilityCheck";

  @Override
  public void apply(@Nonnull Project project) {
    project.getPlugins().apply(BasePlugin.class);
    project.getPlugins().apply(com.commercehub.gradle.plugin.avro.AvroPlugin.class);
    var extension =
        project.getExtensions().create(EXTENSION_NAME, AvroPluginExtension.class, project);

    this.addIdlAsResources(project, extension);
    this.addCheckCompatibilityTask(project, extension);
  }

  private void addIdlAsResources(Project project, AvroPluginExtension extension) {
    project
        .getExtensions()
        .getByType(SourceSetContainer.class)
        .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        .getResources()
        .srcDir(extension.avroSource);
  }

  private void addCheckCompatibilityTask(Project project, AvroPluginExtension extension) {
    var compatibilityTask =
        project
            .getTasks()
            .register(
                COMPATIBILITY_CHECK_TASK_NAME,
                CheckAvroCompatibility.class,
                task -> {
                  var previousArtifactConfig =
                      this.createPreviousArtifactDependency(project, extension);
                  if (previousArtifactConfig.getDependencies().size() > 0) {
                    task.againstFiles(getAvdlProviderForConfig(project, previousArtifactConfig));
                  }

                  var relocatedArtifactConfig =
                      this.createRelocatedArtifactDependency(project, extension);
                  if (relocatedArtifactConfig.getDependencies().size() > 0) {
                    task.source(getAvdlProviderForConfig(project, relocatedArtifactConfig));
                  }

                  task.source(extension.avroSource.get());
                });

    project
        .getTasks()
        .named(LifecycleBasePlugin.CHECK_TASK_NAME)
        .configure(task -> task.dependsOn(compatibilityTask));
  }

  private Configuration createPreviousArtifactDependency(
      Project project, AvroPluginExtension extension) {
    var config =
        this.buildNewDetachedConfiguration(project)
            .resolutionStrategy(
                resolutionStrategy ->
                    resolutionStrategy.cacheDynamicVersionsFor(1, TimeUnit.MINUTES));

    if (extension.previousArtifact.isPresent()) {
      config
          .getDependencies()
          .add(project.getDependencies().create(extension.previousArtifact.get()));
    }

    return config;
  }

  private Configuration createRelocatedArtifactDependency(
      Project project, AvroPluginExtension extension) {
    var config = this.buildNewDetachedConfiguration(project);

    if (extension.relocatedToArtifact.isPresent()) {
      config
          .getDependencies()
          .add(project.getDependencies().create(extension.relocatedToArtifact.get()));
    }

    return config;
  }

  private Provider<FileTree> getAvdlProviderForConfig(
      Project project, Configuration configuration) {
    return project.provider(
        () -> {
          var file = configuration.getSingleFile();
          project.getLogger().info("Using {}", file);
          return project
              .zipTree(configuration.getSingleFile())
              .matching(pattern -> pattern.include("**/*.avdl"));
        });
  }

  private Configuration buildNewDetachedConfiguration(Project project) {
    // This is hacky - we don't want to use the applied project as our resolution context,
    // as this project is often the most recent version of itself. We instead use the root project,
    // since we're detaching the config anyway. This could break for a single project build
    // TODO: look for better way

    var config = project.getRootProject().getConfigurations().detachedConfiguration();
    config.setTransitive(false);
    return config;
  }
}
