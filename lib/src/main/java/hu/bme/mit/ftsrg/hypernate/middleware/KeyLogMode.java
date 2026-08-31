/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

/** Controls how key-like inputs are represented in middleware logs. */
public enum KeyLogMode {
  /** Log only the operation name and non-key metadata. */
  OPERATION_ONLY,
  /** Log the UTF-8 byte length and a SHA-256 hash of the key material. */
  KEY_HASH,
  /** Log composite key shape only, or the length of non-composite key material. */
  KEY_PREFIX,
  /** Log the full key or query input. */
  FULL_KEY
}
