/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

/** Sort directions supported by CouchDB rich queries. */
public enum SortOrder {
  /** Ascending sort order. */
  ASC("asc"),
  /** Descending sort order. */
  DESC("desc");

  private final String jsonValue;

  SortOrder(final String jsonValue) {
    this.jsonValue = jsonValue;
  }

  String jsonValue() {
    return jsonValue;
  }
}
