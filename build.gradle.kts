import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import groovy.util.Node
import org.hypertrace.gradle.publishing.License.AGPL_V3

plugins {
  `java-gradle-plugin`
  id("org.hypertrace.ci-utils-plugin") version "0.1.0"
  id("org.hypertrace.publish-plugin") version "0.1.0"
  id("org.hypertrace.repository-plugin") version "0.1.0"
  id("com.github.johnrengelman.shadow") version "6.0.0"
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
  license.set(AGPL_V3)
}

repositories {
  maven("http://packages.confluent.io/maven")
  jcenter()
}

dependencies {
  shadow(gradleApi())
  implementation("com.commercehub.gradle.plugin:gradle-avro-plugin:0.19.0")
  // avro - compiler, tools
  implementation("org.apache.avro:avro-compiler:1.9.0")
  // for compatibility checker library
  implementation("io.confluent:kafka-schema-registry-client:5.4.2")

  constraints {
    implementation("com.google.guava:guava:24.1.1-jre") {
      because("Vulnerability in schema registry client, fixed in upcoming 6.0: https://snyk.io/vuln/SNYK-JAVA-COMGOOGLEGUAVA-32236")
    }

    implementation("org.yaml:snakeyaml:1.26") {
      because("Vulnerability in schema registry client, fixed in upcoming 6.0: https://snyk.io/vuln/SNYK-JAVA-ORGYAML-537645")
    }
  }
}

tasks.jar {
  enabled = false;
}

val relocationTask = tasks.register<ConfigureShadowRelocation>("relocatePackages") {
  target = tasks.shadowJar.get()
  prefix = "org.hypertrace.shaded"
}
tasks.shadowJar {
  dependsOn(relocationTask)
  minimize()
  archiveClassifier.set("")
}
tasks.assemble {
  dependsOn(tasks.shadowJar)
}

publishing {
  publications {
    afterEvaluate {
      named<MavenPublication>("pluginMaven") {
        // We need to remove the old publication and replace it with the far jar (and update the
        // pom to reflect the new dependencies)
        setArtifacts(emptySet<Artifact>())
        pom {
          withXml {
            // Remove all of the dependencies as they don't apply to shadow. I'm sorry, the APIs are terrible.
            (asNode().get("dependencies") as groovy.util.NodeList?)?.forEach { dependencyNode ->
              asNode().remove(dependencyNode as Node?)
            }
          }
        }
        shadow.component(this)
      }
    }
  }
}
