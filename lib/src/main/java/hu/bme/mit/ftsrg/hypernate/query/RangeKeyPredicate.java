/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

/**
 * Predicate builder for a single primary-key component in a range query.
 *
 * @param <T> the entity type returned by the enclosing builder
 */
public interface RangeKeyPredicate<T> {

  /**
   * Constrain the primary-key component to the provided value.
   *
   * @param value the key part value; must match the type declared in {@code @AttributeInfo} for
   *     this key position
   * @return the parent range-query builder for continued chaining
   * @throws IllegalArgumentException if the value type is incompatible with the declared attribute
   *     mapper for this key field
   */
  RangeQueryBuilder<T> is(Object value);
}
