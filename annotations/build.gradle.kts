/* SPDX-License-Identifier: Apache-2.0 */

plugins {
  `java-library`
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

group = "hu.bme.mit.ftsrg"

version = "0.1.0"

repositories {
  mavenCentral()
}
