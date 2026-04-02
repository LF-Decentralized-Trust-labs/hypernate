/* SPDX-License-Identifier: Apache-2.0 */

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  `java-library`
  `maven-publish`
  id("com.github.johnrengelman.shadow") version "8.1.1"
  id("com.diffplug.spotless") version "6.20.0"
  id("com.adarshr.test-logger") version "3.2.0"
  id("io.freefair.lombok") version "8.6"
  id("io.freefair.aspectj.post-compile-weaving") version "8.6"
}

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
  withSourcesJar()
  withJavadocJar()
}

group = "hu.bme.mit.ftsrg"

version = "0.1.0"

repositories {
  mavenCentral()
  maven { url = uri("https://jitpack.io") }
}

dependencies {
  implementation("org.slf4j:slf4j-api:2.0.13")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
  implementation("com.jcabi:jcabi-aspects:0.26.0")
  implementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.0")
  implementation("org.hyperledger.fabric:fabric-protos:0.3.0")

  aspect("com.jcabi:jcabi-aspects:0.26.0")

  testImplementation("org.slf4j:slf4j-simple:2.0.13")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
  testImplementation("org.mockito:mockito-core:5.11.0")
  testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

val integrationTest by sourceSets.creating

integrationTest.compileClasspath += sourceSets.main.get().output

integrationTest.runtimeClasspath += sourceSets.main.get().output

configurations[integrationTest.implementationConfigurationName].extendsFrom(
    configurations.testImplementation.get())

configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(
    configurations.testRuntimeOnly.get())

tasks.withType<JavaCompile> {
  options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.test {
  useJUnitPlatform()
  testLogging {
    showExceptions = true
    showStandardStreams = true
    exceptionFormat = TestExceptionFormat.FULL
    events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
  }
}

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
      description = "Runs integration tests."
      group = "verification"
      testClassesDirs = integrationTest.output.classesDirs
      classpath = integrationTest.runtimeClasspath
      shouldRunAfter(tasks.test)
      useJUnitPlatform()
      testLogging {
        showExceptions = true
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
      }
    }

tasks.check { dependsOn(integrationTestTask) }

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])

      pom {
        name.set("hypernate")
        description.set("Entity framework for Hyperledger Fabric smart contracts")
        url.set("https://github.com/LF-Decentralized-Trust-labs/hypernate")
        licenses {
          license {
            name.set("Apache License 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0")
          }
        }
        scm {
          connection.set("scm:git:https://github.com/LF-Decentralized-Trust-labs/hypernate.git")
          developerConnection.set(
              "scm:git:ssh://git@github.com/LF-Decentralized-Trust-labs/hypernate.git")
          url.set("https://github.com/LF-Decentralized-Trust-labs/hypernate")
        }
      }
    }
  }

  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/LF-Decentralized-Trust-labs/hypernate")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}

spotless {
  java {
    importOrder()
    removeUnusedImports()
    googleJavaFormat()
    formatAnnotations()
    toggleOffOn()
    licenseHeader("/* SPDX-License-Identifier: Apache-2.0 */", "package ")
  }
  kotlin {
    target("src/*/kotlin/**/*.kt", "buildSrc/src/*/kotlin/**/*.kt")
    ktfmt()
    licenseHeader("/* SPDX-License-Identifier: Apache-2.0 */", "package ")
  }
  kotlinGradle { ktfmt() }
}
