/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

/**
 * Predicate builder for a single rich-query field.
 *
 * @param <T> the entity type returned by the enclosing builder
 */
public interface FieldPredicate<T> {

  /**
   * Constrain the field to equal the provided value.
   *
   * @param value the value to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> is(Object value);

  /**
   * Constrain the field to not equal the provided value.
   *
   * @param value the value to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> isNot(Object value);

  /**
   * Constrain the field to be greater than the provided value.
   *
   * @param value the value to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> greaterThan(Object value);

  /**
   * Constrain the field to be greater than or equal to the provided value.
   *
   * @param value the value to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> greaterThanOrEqualTo(Object value);

  /**
   * Constrain the field to be less than the provided value.
   *
   * @param value the value to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> lessThan(Object value);

  /**
   * Constrain the field to be less than or equal to the provided value.
   *
   * @param value the value to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> lessThanOrEqualTo(Object value);

  /**
   * Constrain the field to match any of the provided values.
   *
   * @param values the values to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> in(Object... values);

  /**
   * Constrain the field to reject all of the provided values.
   *
   * @param values the values to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> notIn(Object... values);

  /**
   * Constrain the field to either exist or be absent.
   *
   * @param exists the value to compare against; must not be null
   * @return the parent rich-query builder for continued chaining
   */
  RichQueryBuilder<T> exists(boolean exists);
}
