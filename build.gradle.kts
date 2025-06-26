import org.hypertrace.gradle.publishing.License.APACHE_2_0

plugins {
  `java-gradle-plugin`
  id("org.hypertrace.ci-utils-plugin") version "0.4.0"
  id("org.hypertrace.publish-plugin") version "1.1.1"
  id("org.hypertrace.repository-plugin") version "0.5.0"
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.hypertrace.gradle.avro"

java {
  targetCompatibility = JavaVersion.VERSION_11
  sourceCompatibility = JavaVersion.VERSION_11
}

gradlePlugin {
  plugins {
    create("gradlePlugin") {
      id = "org.hypertrace.avro-plugin"
      implementationClass = "org.hypertrace.gradle.avro.AvroPlugin"
    }
  }
}

hypertracePublish {
  license.set(APACHE_2_0)
}

val bundled by configurations.creating {
  setTransitive(false);
}

configurations.implementation {
  extendsFrom(bundled)
}

dependencies {
  shadow("com.github.davidmc24.gradle.plugin:gradle-avro-plugin:1.3.0")
  // avro - compiler, tools
  shadow("org.apache.avro:avro-compiler:1.11.1")
  // for compatibility checker library
  bundled("io.confluent:kafka-schema-registry-client:6.2.6")
}

tasks.jar {
  enabled = false;
}

tasks.shadowJar {
  minimize()
  archiveClassifier.set("")
  configurations = listOf(bundled)
  relocate("io.confluent.kafka", "org.hypertrace.shaded.io.confluent.kafka")
}
tasks.assemble {
  dependsOn(tasks.shadowJar)
}

publishing {
  publications {
    register<MavenPublication>("fatPluginMaven") {
      shadow.component(this)
    }
  }
}

tasks.whenObjectAdded {
  enabled = !name.startsWith("publishPluginMavenPublication")
}
