/* SPDX-License-Identifier: Apache-2.0 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  `java-library`
  id("com.github.johnrengelman.shadow") version "8.1.1"
  checkstyle
  id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
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

checkstyle {
  toolVersion = "10.21.4"
  configFile = rootProject.file("config/checkstyle/google_checks.xml")
  configProperties["org.checkstyle.google.severity"] = "error"
}

ktlint {
  version.set("1.3.1")
  verbose.set(true)
}
