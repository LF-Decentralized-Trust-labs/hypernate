/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

final class PublicStateStore implements EntityStateStore {

  private final ChaincodeStub stub;

  PublicStateStore(final ChaincodeStub stub) {
    this.stub = stub;
  }

  @Override
  public byte[] get(final String key) {
    return stub.getState(key);
  }

  @Override
  public void put(final String key, final byte[] value) {
    stub.putState(key, value);
  }

  @Override
  public void delete(final String key) {
    stub.delState(key);
  }

  @Override
  public QueryResultsIterator<KeyValue> getByPartialCompositeKey(final String key) {
    return stub.getStateByPartialCompositeKey(key);
  }
}
