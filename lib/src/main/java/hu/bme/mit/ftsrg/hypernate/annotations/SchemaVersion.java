/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the explicit schema version of the annotated class to be used in accessing schema
 * versions.
 *
 * <p>This annotation is optional. In its absence the SchemaRegistry defaults the version to 1.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @SchemaVersion(1)
 * @PrimaryKey(@AttributeInfo(name = Asset.Fields.id))
 * public record Asset(String id, String color, int size) {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SchemaVersion {
  int value();
}
