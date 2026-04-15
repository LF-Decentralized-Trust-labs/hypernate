/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Defines the middleware chain for the contract. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiddlewareInfo {
  Class<? extends StubMiddleware>[] value();
}
