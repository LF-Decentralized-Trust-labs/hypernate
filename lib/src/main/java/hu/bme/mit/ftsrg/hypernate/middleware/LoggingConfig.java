/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Contract-level logging configuration for {@link LoggingStubMiddleware}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoggingConfig {
  /** Default key log mode for public state and any collection without an override. */
  KeyLogMode defaultKeyMode() default KeyLogMode.KEY_PREFIX;

  /** Default value log mode for public state and any collection without an override. */
  ValueLogMode defaultValueMode() default ValueLogMode.KEYS_ONLY;

  /** Optional per-collection overrides. */
  CollectionLogConfig[] collections() default {};
}
