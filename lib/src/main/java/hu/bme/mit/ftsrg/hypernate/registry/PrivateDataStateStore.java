/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

final class PrivateDataStateStore implements EntityStateStore {

  private final ChaincodeStub stub;
  private final String collection;

  PrivateDataStateStore(final ChaincodeStub stub, final String collection) {
    this.stub = stub;
    this.collection = collection;
  }

  @Override
  public byte[] get(final String key) {
    return stub.getPrivateData(collection, key);
  }

  @Override
  public void put(final String key, final byte[] value) {
    stub.putPrivateData(collection, key, value);
  }

  @Override
  public void delete(final String key) {
    stub.delPrivateData(collection, key);
  }

  @Override
  public QueryResultsIterator<KeyValue> getByPartialCompositeKey(final String key) {
    return stub.getPrivateDataByPartialCompositeKey(collection, key);
  }
}
