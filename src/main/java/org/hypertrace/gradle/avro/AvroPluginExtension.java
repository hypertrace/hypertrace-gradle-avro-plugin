package org.hypertrace.gradle.avro;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public class AvroPluginExtension {

  public final Property<Object> avroSource;
  public final Property<String> previousArtifact;
  public final Property<String> relocatedToArtifact;

  public AvroPluginExtension(Project project) {
    this.avroSource = project.getObjects().property(Object.class)
        .value("src/main/avro");
    this.previousArtifact = project.getObjects().property(String.class)
        .value(project.getGroup() + ":" + project.getName() + ":latest.release");
    this.relocatedToArtifact = project.getObjects().property(String.class);
  }
}
