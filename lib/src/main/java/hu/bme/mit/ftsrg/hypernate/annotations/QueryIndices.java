/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the query indices of an asset.
 *
 * <p>You must pass an array of {@link QueryIndex}es.
 *
 * <p>Usage example:
 *
 * <blockquote>
 *
 * <pre><code>
 * {@literal @}PrimaryKey(
 *   {@literal @}AttributeInfo("id")
 *  })
 * {@literal @}QueryIndices({
 *   {@literal @}QueryIndex(
 *      name = "name",
 *      attributes = {
 *       {@literal @}AttributeInfo("firstName"),
 *       {@literal @}AttributeInfo("lastName")
 *      }
 *   })
 *  })
 *  public record Person(
 *   {@literal @}Property String id,
 *   {@literal @}Property String firstName,
 *   {@literal @}Property String lastName,
 *   {@literal @}Property Integer numberOfPets
 *  );</code></pre>
 *
 * </blockquote>
 *
 * @see QueryIndex
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QueryIndices {
  /**
   * The array of query indices defined for the asset.
   *
   * @return the array of query indices defined
   */
  QueryIndex[] value();
}
