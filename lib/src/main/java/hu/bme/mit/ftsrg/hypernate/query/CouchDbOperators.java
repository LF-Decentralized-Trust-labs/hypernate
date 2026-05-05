/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

final class CouchDbOperators {

  static final String EQ = "$eq";
  static final String NE = "$ne";
  static final String GT = "$gt";
  static final String GTE = "$gte";
  static final String LT = "$lt";
  static final String LTE = "$lte";
  static final String IN = "$in";
  static final String NIN = "$nin";
  static final String EXISTS = "$exists";
  static final String AND = "$and";
  static final String OR = "$or";

  private CouchDbOperators() {}
}
