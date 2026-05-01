/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

/** Controls how values are represented in middleware logs. */
public enum ValueLogMode {
  /** Do not log value contents or metadata. */
  KEYS_ONLY,
  /** Log only byte length and a SHA-256 hash. */
  VALUE_METADATA,
  /** Interpret bytes as UTF-8 and log the full text. */
  VALUE_UTF8,
  /** Interpret bytes as UTF-8 and log only a fixed-size prefix. */
  VALUE_UTF8_TRUNCATED
}
