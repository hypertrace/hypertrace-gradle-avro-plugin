package org.hypertrace.gradle.avro;

import io.confluent.kafka.schemaregistry.avro.AvroCompatibilityChecker;
import io.confluent.kafka.schemaregistry.avro.AvroCompatibilityLevel;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.avro.Schema;
import org.apache.avro.compiler.idl.Idl;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Verifies the compatibility of the two avro idl sets provided.
 *
 * <h4>How this works?</h4>
 * <li>Parses the input sets of avro idl files (with .avdl extension only) using avro-compiler</li>
 * <li>Fails in case of any parsing errors of idl (Ex: incorrect idl, syntax errors)</li>
 * <li>Fails when source schema is not found for at least one of the against schema.</li>
 * <li>Finally, checks for the compatibility of each base schema with its corresponding latest
 * schema with namespace and name.</li>
 * <li>Fails fast with an error on encountering the first incompatible schema.</li>
 * <h4>Assumptions</h4>
 * <li>Verifies for the full transitive compatibility of the schema</li>
 */
public class CheckAvroCompatibility extends SourceTask {
  private final Logger log = getProject().getLogger();
  private ConfigurableFileCollection againstFiles = this.getProject().files();
  private final AvroCompatibilityChecker compatibilityChecker =
      AvroCompatibilityLevel.FULL_TRANSITIVE.compatibilityChecker;

  @InputFiles
  @SkipWhenEmpty
  public FileCollection getAgainstFiles() {
    return againstFiles;
  }

  public void setAgainstFiles(Object againstFiles) {
    this.againstFiles = this.getProject().files(againstFiles);
  }

  public CheckAvroCompatibility againstFiles(Object... sources) {
    this.againstFiles.from(sources);
    return this;
  }

  @Inject
  public CheckAvroCompatibility() {
    this.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
    this.setDescription("Runs avro compatibility check between source and againstFiles");
  }

  @TaskAction
  protected void runCheck() {
    Map<String, Schema> sourceSchemas = loadSchemas(getSource().getFiles());
    log.info("parsed source schemas: {}", sourceSchemas.keySet());

    Map<String, Schema> againstSchemas = loadSchemas(againstFiles.getAsFileTree().getFiles());
    log.info("parsed against schemas: {}", againstSchemas.keySet());

    Set<String> againstSchemaSet = new HashSet<>(againstSchemas.keySet());
    Set<String> sourceSchemaSet = new HashSet<>(sourceSchemas.keySet());
    againstSchemaSet.removeAll(sourceSchemaSet);

    if (againstSchemaSet.size() != 0) {
      throw new TaskExecutionException(
          this,
          new Exception(
              "schema(s) missing from source set. diff size: "
                  + againstSchemaSet.size()
                  + ", diff: "
                  + againstSchemaSet));
    }

    sourceSchemas.entrySet().stream()
        .forEach(
            entry -> {
              Schema sourceSchema = sourceSchemas.get(entry.getKey());
              Schema againstSchema = againstSchemas.get(entry.getKey());
              if (againstSchema == null) {
                log.info(
                    "ignoring source schema for which corresponding against schema not available. schema name: {}.{}.",
                    sourceSchema.getNamespace(),
                    sourceSchema.getName());
              } else {
                boolean isCompatible =
                    compatibilityChecker.isCompatible(sourceSchema, againstSchema);
                log.debug(
                    "verified compatibility for the schema: {}.{}, result: {}",
                    againstSchema.getNamespace(),
                    againstSchema.getName(),
                    isCompatible);
                if (!isCompatible) {
                  throw new TaskExecutionException(
                      this,
                      new Exception(
                          String.format(
                              "Schema incompatibility found for the schema: %s.%s",
                              sourceSchema.getNamespace(), sourceSchema.getName())));
                }
              }
            });
  }

  private Map<String, Schema> loadSchemas(Set<File> idlFiles) {
    return idlFiles.stream()
        .flatMap(file -> parseIdl(file).stream())
        .distinct()
        .collect(
            Collectors.toMap(
                schema -> schema.getNamespace() + "." + schema.getName(), Function.identity()));
  }

  private Collection<Schema> parseIdl(File file) {
    try (Idl idl = new Idl(file)) {
      return idl.CompilationUnit().getTypes();
    } catch (Throwable e) {
      throw new TaskExecutionException(this, new Exception("error while parsing idl: " + file, e));
    }
  }
}
