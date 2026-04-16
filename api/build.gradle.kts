/* SPDX-License-Identifier: Apache-2.0 */

plugins {
  `java-library`
  id("io.freefair.lombok") version "8.6"
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

group = "hu.bme.mit.ftsrg"

version = "0.1.0"

repositories {
  mavenCentral()
  maven { url = uri("https://jitpack.io") }
}

dependencies {
  api(project(":annotations"))

  implementation("org.slf4j:slf4j-api:2.0.13")
  compileOnly("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.0")
  compileOnly("org.hyperledger.fabric:fabric-protos:0.3.0")
}
