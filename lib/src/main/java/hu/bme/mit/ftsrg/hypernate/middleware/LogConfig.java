/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import java.util.Objects;

/** Runtime logging configuration for a public state space or private collection. */
public record LogConfig(KeyLogMode keyMode, ValueLogMode valueMode) {

  public LogConfig {
    Objects.requireNonNull(keyMode, "keyMode cannot be null");
    Objects.requireNonNull(valueMode, "valueMode cannot be null");
  }
}
