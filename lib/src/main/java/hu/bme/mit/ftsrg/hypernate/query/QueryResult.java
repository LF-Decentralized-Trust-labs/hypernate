/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable wrapper around query results.
 *
 * @param <T> the entity type contained in the result
 */
public final class QueryResult<T> implements Iterable<T> {

  private final List<T> results;

  /**
   * Create a query result wrapper.
   *
   * @param results the query results to wrap
   */
  public QueryResult(final List<T> results) {
    this.results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
  }

  /**
   * Return the wrapped results as an immutable list.
   *
   * @return the wrapped results
   */
  public List<T> asList() {
    return results;
  }

  /**
   * Return the number of wrapped entities.
   *
   * @return the result size
   */
  public int size() {
    return results.size();
  }

  /**
   * Return whether the wrapped result is empty.
   *
   * @return {@code true} if no entities were returned, {@code false} otherwise
   */
  public boolean isEmpty() {
    return results.isEmpty();
  }

  /**
   * Returns an iterator over the query result entities.
   *
   * @return an iterator over the wrapped query result entities
   */
  @Override
  public Iterator<T> iterator() {
    return results.iterator();
  }
}
