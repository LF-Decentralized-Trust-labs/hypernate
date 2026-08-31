/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import java.lang.annotation.*;

/**
 * Describes a new set of attributes supporting indexed queries.
 *
 * <p>This annotation should be used inside {@link QueryIndices @QueryIndices}. You must specify a
 * name for the index and an array of attributes that comprise the index using {@link
 * AttributeInfo}.
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
 * @see AttributeInfo
 * @see QueryIndices
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(QueryIndices.class)
public @interface QueryIndex {
  /**
   * The name of the index.
   *
   * @return the name of the index
   */
  String name();

  /**
   * The attributes included in the index.
   *
   * @return the attributes included in the index
   */
  AttributeInfo[] attributes();
}
