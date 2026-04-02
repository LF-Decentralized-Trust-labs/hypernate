/* SPDX-License-Identifier: Apache-2.0 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  `java-library`
  id("com.github.johnrengelman.shadow") version "8.1.1"
  id("com.diffplug.spotless") version "6.20.0"
  id("com.adarshr.test-logger") version "3.2.0"
  id("io.freefair.lombok") version "8.6"
  id("io.freefair.aspectj.post-compile-weaving") version "8.6"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

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
  implementation("io.opentelemetry:opentelemetry-api:1.44.1")
  implementation("io.opentelemetry:opentelemetry-context:1.44.1")
  implementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.0")
  implementation("org.hyperledger.fabric:fabric-protos:0.3.0")

  aspect("com.jcabi:jcabi-aspects:0.26.0")

  testImplementation("org.slf4j:slf4j-simple:2.0.13")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
  testImplementation("org.mockito:mockito-core:5.11.0")
  testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

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
