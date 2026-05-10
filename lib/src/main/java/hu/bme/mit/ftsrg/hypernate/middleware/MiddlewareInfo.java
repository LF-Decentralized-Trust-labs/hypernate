/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the middleware chain for the contract.
 *
 * <p>You should use this annotation on your contract class; eg:
 *
 * <blockquote>
 *
 * <pre><code>
 * {@literal @}MiddlewareInfo({LoggingStubMiddleware.class})
 *  public class MyContract implements ContractInterface {
 *    // ...
 *  }</code></pre>
 *
 * </blockquote>
 *
 * @see StubMiddlewareChain
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiddlewareInfo {
  /**
   * The array of stub middleware classes to instantiate in the chain.
   *
   * @return the array of stub middleware classes to instantiate in the chain
   */
  Class<? extends StubMiddleware>[] value();
}
