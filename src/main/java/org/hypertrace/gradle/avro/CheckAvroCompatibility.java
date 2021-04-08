package org.hypertrace.gradle.avro;

import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
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
 *
 * <li>Parses the input sets of avro idl files (with .avdl extension only) using avro-compiler
 * <li>Fails in case of any parsing errors of idl (Ex: incorrect idl, syntax errors)
 * <li>Fails when source schema is not found for at least one of the against schema.
 * <li>Finally, checks for the compatibility of each base schema with its corresponding latest
 *     schema with namespace and name.
 * <li>Fails fast with an error on encountering the first incompatible schema.
 *
 *     <h4>Assumptions</h4>
 *
 * <li>Verifies for the full transitive compatibility of the schema
 */
public class CheckAvroCompatibility extends SourceTask {
  private final Logger log = getProject().getLogger();
  private ConfigurableFileCollection againstFiles = this.getProject().files();
  private final CompatibilityChecker compatibilityChecker =
      CompatibilityChecker.FULL_TRANSITIVE_CHECKER;

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
    Map<String, AvroSchema> sourceSchemas = loadSchemas(getSource().getFiles());
    log.info("parsed source schemas: {}", sourceSchemas.keySet());

    Map<String, AvroSchema> againstSchemas = loadSchemas(againstFiles.getAsFileTree().getFiles());
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
              AvroSchema sourceSchema = sourceSchemas.get(entry.getKey());
              AvroSchema againstSchema = againstSchemas.get(entry.getKey());
              if (againstSchema == null) {
                log.info(
                    "ignoring source schema for which corresponding against schema not available. schema name: {}",
                    sourceSchema.rawSchema().getFullName());
              } else {
                List<String> validationErrors =
                    compatibilityChecker.isCompatible(
                        sourceSchema, Collections.singletonList(againstSchema));
                boolean isCompatible = validationErrors.isEmpty();
                log.debug(
                    "verified compatibility for the schema: {}, result: {}",
                    againstSchema.rawSchema().getFullName(),
                    isCompatible);
                if (!isCompatible) {
                  throw new TaskExecutionException(
                      this,
                      new Exception(
                          String.format(
                              "Schema incompatibility found for the schema: %s.\n%s",
                              sourceSchema.rawSchema().getFullName(), validationErrors)));
                }
              }
            });
  }

  private Map<String, AvroSchema> loadSchemas(Set<File> idlFiles) {
    return idlFiles.stream()
        .flatMap(this::parseIdl)
        .distinct()
        .collect(Collectors.toMap(schema -> schema.rawSchema().getFullName(), Function.identity()));
  }

  private Stream<AvroSchema> parseIdl(File file) {
    try (Idl idl = new Idl(file)) {
      return idl.CompilationUnit().getTypes().stream()
          .map(schema -> new AvroSchema(schema.toString()));
    } catch (Throwable e) {
      throw new TaskExecutionException(this, new Exception("error while parsing idl: " + file, e));
    }
  }
}
