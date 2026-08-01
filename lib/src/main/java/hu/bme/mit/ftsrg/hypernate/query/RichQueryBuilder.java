/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import java.util.List;

/**
 * Fluent builder for CouchDB rich queries.
 *
 * @param <T> the entity type returned by the query
 */
public interface RichQueryBuilder<T> {

  /**
   * Start a selector clause for the given entity field.
   *
   * @param field the entity field to constrain
   * @return a predicate builder bound to the provided field so a comparison operator can be chosen
   * @throws IllegalArgumentException if the field name is blank or does not match the entity class
   */
  FieldPredicate<T> where(String field);

  /**
   * Add another selector clause combined with logical {@code $and}.
   *
   * @param field the entity field to constrain
   * @return a predicate builder bound to the provided field so another comparison can be chained
   */
  FieldPredicate<T> and(String field);

  /**
   * Add another selector clause combined with logical {@code $or}.
   *
   * @param field the entity field to constrain
   * @return a predicate builder bound to the provided field so another comparison can be chained
   */
  FieldPredicate<T> or(String field);

  /**
   * Add a sort clause to the query.
   *
   * @param field the entity field to sort by
   * @param order the sort direction
   * @return the current builder instance so additional query clauses can be chained
   */
  RichQueryBuilder<T> sortBy(String field, SortOrder order);

  /**
   * Limit the number of returned entities.
   *
   * @param n the maximum number of entities to return
   * @return the current builder instance so additional query clauses can be chained
   * @throws IllegalArgumentException if {@code n} is negative
   */
  RichQueryBuilder<T> limit(int n);

  /**
   * Skip a number of matching entities before returning results.
   *
   * @param n the number of matching entities to skip
   * @return the current builder instance so additional query clauses can be chained
   * @throws IllegalArgumentException if {@code n} is negative
   */
  RichQueryBuilder<T> skip(int n);

  /**
   * Acknowledge that the current transaction may contain uncommitted cached writes.
   *
   * @return the current builder instance with stale-read warnings explicitly suppressed
   */
  RichQueryBuilder<T> acknowledgeStaleReads();

  /**
   * Execute the assembled rich query.
   *
   * @return the matching entities, possibly empty
   * @throws HypernateException if ledger access, entity serialization, or deserialization fails
   * @throws IllegalArgumentException if the query references an unknown entity field
   */
  List<T> execute() throws HypernateException;
}
