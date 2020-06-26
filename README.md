# Hypertrace Avro Plugin
###### org.hypertrace.avro-plugin

[![CircleCI](https://circleci.com/gh/hypertrace/hypertrace-gradle-avro-plugin.svg?style=svg)](https://circleci.com/gh/hypertrace/hypertrace-gradle-avro-plugin)
### Purpose
This plugin serves to transpile avro files to java and check them for compatibility. It also
adds the IDL files as resources to the artifact.

#### How this works?
- Parses the input sets of avro idl files (with .avdl extension only) using avro-compiler
- Fails in case of any parsing errors of idl (Ex: incorrect idl, syntax errors)
- Fails when source schema is not found for at least one of the against schema.
- Finally, checks for the compatibility of each base schema with its corresponding latest schema with namespace and name.
- Fails fast with an error on encountering the first incompatible schema.

### Tasks

#### avroCompatibilityCheck
Verifies the compatibility of the two sets of avro idls. This task is part of the verification group
and will be run as part of `check` (and thus also by `build`).

Example usage:
```
./gradlew avroCompatibilityCheck
```

### Usage
Is a superset of the commercehub avro plugin (which it applies), so that can be removed.

```kotlin

 plugins {
     // ...
-    id("com.commercehub.gradle.plugin.avro") version "0.9.1"
+    id("org.hypertrace.avro-plugin") version "<version>"
 }

// values below marked as DEFAULT do not need to be set if unchanged. They can be overridden by changing these values.
hypertraceAvro {
  avroSource.set("src/main/avro") // DEFAULT
  previousArtifact.set("${project.group}/${project.name}:latest.release") // DEFAULT
  relocatedToArtifact.set("org.hypertrace.foo:bar:1.5.6") // Treat avdl files here as source files for comparison too (Default: unset)
}
```