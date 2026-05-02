/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import org.hyperledger.fabric.shim.ChaincodeStub;

public final class PrivateDataRegistry extends Registry {

  PrivateDataRegistry(final ChaincodeStub stub, final String collection) {
    super(stub, new PrivateDataStateStore(stub, collection));
  }
}
