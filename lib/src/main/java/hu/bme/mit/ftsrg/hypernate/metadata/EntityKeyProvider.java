/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import org.hyperledger.fabric.shim.ChaincodeStub;

/**
 * Abstraction layer to provide ledger keys and associated information for entity instances.
 *
 * <p>These are meant to be handed out from {@link EntityMetadataProvider}.
 */
public interface EntityKeyProvider {

  /**
   * Construct a ledger key for the passed entity instance.
   *
   * @param entity the entity whose key is to be created
   * @return the key that can be used for this entity, eg with {@link ChaincodeStub#putState}.
   */
  String getKey(final Object entity);

  /**
   * Construct a ledger key for an entity from just key parts.
   *
   * @param keyPart the number of known key parts
   * @return the key constructed from those parts
   */
  String getKeyForParts(final Object... keyPart);

  /**
   * Return the type of the entity as known by Hypernate.
   *
   * @return the entity's type
   */
  String getType();
}
