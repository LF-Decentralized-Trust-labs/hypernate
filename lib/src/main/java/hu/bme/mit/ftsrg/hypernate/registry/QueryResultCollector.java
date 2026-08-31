/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import java.util.List;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class QueryResultCollector {
  private static final Logger logger = LoggerFactory.getLogger(QueryResultCollector.class);

  static <T> List<T> collect(Iterable<KeyValue> iterable, Class<T> clazz) {
    List<T> results = new java.util.ArrayList<>();
    for (KeyValue kv : iterable) {
      logger.debug("Deserializing entity at key: {}", kv.getKey());
      results.add(Registry.EntityUtil.fromBuffer(kv.getValue(), clazz));
    }
    return results;
  }
}
