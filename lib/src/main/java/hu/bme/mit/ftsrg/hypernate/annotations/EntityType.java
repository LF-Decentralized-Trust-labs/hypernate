/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the explicit entity type name of the annotated class to be used for Registry composite
 * keys.
 *
 * <p>This annotation is optional. In its absence, the entity type name defaults to the fully
 * qualified class name (FQCN) returned by {@link Class#getName()}.
 *
 * <p>Specifying a stable, explicit name is recommended to decouple the keys in the ledger from
 * potential Java class/package renames.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @EntityType("Asset")
 * @PrimaryKey(@AttributeInfo(name = Asset.Fields.id))
 * public record Asset(String id, String color, int size) {}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntityType {
  String value();
}
