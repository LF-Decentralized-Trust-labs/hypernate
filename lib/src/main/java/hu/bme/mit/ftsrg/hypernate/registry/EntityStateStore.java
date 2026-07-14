/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

interface EntityStateStore {

  byte[] get(String key);

  void put(String key, byte[] value);

  void delete(String key);

  QueryResultsIterator<KeyValue> getByPartialCompositeKey(String key);
}
