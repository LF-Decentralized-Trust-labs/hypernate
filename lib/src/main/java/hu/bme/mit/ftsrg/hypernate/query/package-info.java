/* SPDX-License-Identifier: Apache-2.0 */
/**
 * Fluent query builders for Hypernate registry access.
 *
 * <p>Rich queries are executed as CouchDB selector queries and may only observe committed ledger
 * state, while range queries are executed as composite-key scans and therefore follow primary-key
 * prefix semantics.
 */
package hu.bme.mit.ftsrg.hypernate.query;
