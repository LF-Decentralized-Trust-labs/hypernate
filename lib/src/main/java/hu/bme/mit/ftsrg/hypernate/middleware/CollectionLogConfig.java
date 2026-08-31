/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Per-collection logging configuration for {@link LoggingStubMiddleware}. */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface CollectionLogConfig {
  /** Collection name. Use an empty string for public state. */
  String name();

  /** How key-like inputs should be logged for this collection. */
  KeyLogMode keyMode();

  /** How values should be logged for this collection. */
  ValueLogMode valueMode();
}
