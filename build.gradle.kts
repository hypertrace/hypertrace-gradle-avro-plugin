import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import org.hypertrace.gradle.publishing.License.APACHE_2_0
plugins {
  `java-gradle-plugin`
  id("org.hypertrace.ci-utils-plugin") version "0.3.0"
  id("org.hypertrace.publish-plugin") version "1.0.2"
  id("org.hypertrace.repository-plugin") version "0.4.0"
  id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "org.hypertrace.gradle.avro"

java {
  targetCompatibility = JavaVersion.VERSION_1_8
  sourceCompatibility = JavaVersion.VERSION_1_8
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

repositories {
  maven("http://packages.confluent.io/maven")
  jcenter()

}

val bundled by configurations.creating {
  setTransitive(false);
}

configurations.implementation {
  extendsFrom(bundled)
}

dependencies {
  shadow(gradleApi())
  shadow("com.commercehub.gradle.plugin:gradle-avro-plugin:0.19.1")
  // avro - compiler, tools
  shadow("org.apache.avro:avro-compiler:1.9.2")
  // for compatibility checker library
  bundled("io.confluent:kafka-schema-registry-client:5.4.2")

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
  configurations = listOf(bundled)
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
