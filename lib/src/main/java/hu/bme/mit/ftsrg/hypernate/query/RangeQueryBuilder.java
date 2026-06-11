/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import java.util.List;

/**
 * Fluent builder for composite-key range queries.
 *
 * @param <T> the entity type returned by the query
 */
public interface RangeQueryBuilder<T> {

  /**
   * Start a primary-key prefix clause for the given field.
   *
   * @param primaryKeyField the primary-key field to constrain
   * @return a key predicate builder bound to the provided primary-key field
   */
  RangeKeyPredicate<T> whereKey(String primaryKeyField);

  /**
   * Execute the assembled range query.
   *
   * @return the matching entities, possibly empty
   * @throws HypernateException if entity serialization or deserialization fails
   * @throws IllegalArgumentException if the supplied key fields are not a valid primary-key prefix
   */
  List<T> execute() throws HypernateException;
}
