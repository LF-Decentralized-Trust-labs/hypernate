/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the primary key attributes of an asset.
 *
 * <p>You must specify an array of {@link AttributeInfo}s as the {@code value} parameter; for
 * example:
 *
 * <blockquote>
 *
 * <pre><code>
 * {@literal @}PrimaryKey({
 *   {@literal @}AttributeInfo("firstName"),
 *   {@literal @}AttributeInfo("lastName")
 *  })
 *  public record Person(
 *   {@literal @}Property String firstName,
 *   {@literal @}Property String lastName,
 *   {@literal @}Property Integer birthYear,
 *   {@literal @}Property Integer numberOfPets
 *  );</code></pre>
 *
 * </blockquote>
 *
 * @see AttributeInfo
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PrimaryKey {
  /**
   * The attributes that form this (composite) primary key.
   *
   * @return the attributes that form this primary key
   */
  AttributeInfo[] value();
}
