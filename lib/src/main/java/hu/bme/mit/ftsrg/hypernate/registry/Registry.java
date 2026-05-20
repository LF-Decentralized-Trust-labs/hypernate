/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import com.jcabi.aspects.Loggable;

import hu.bme.mit.ftsrg.hypernate.annotations.EntityKeyProvider;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Loggable(Loggable.DEBUG)
public class Registry {

  private static final Logger logger = LoggerFactory.getLogger(Registry.class);

  private final ChaincodeStub stub;

  private final EntityMetaDataProvider entityProvider;

  public Registry(final ChaincodeStub stub) {
    this.stub = stub;
    entityProvider = new EntityMetaDataProvider();
  }

  /**
   * Create a new entity.
   *
   * @param entity the entity to create
   * @param <T>    the entity type
   * @throws EntityExistsException if the entity already exists in the ledger
   */
  public <T> void mustCreate(final T entity) throws EntityExistsException {
    assertNotExists(entity);

    final String key = getCompositeKey(entity);
    final byte[] buffer = entityProvider.toBuffer(entity);
    stub.putState(key, buffer);
  }

  /**
   * Create a new entity unless it already exists.
   *
   * @param entity the entity to create
   * @return {@code true} if a new entity was created, {@code false} otherwise
   * @param <T> the entity type
   */
  public <T> boolean tryCreate(final T entity) {
    try {
      mustCreate(entity);
    } catch (EntityExistsException e) {
      logger.info("{} already exists -- ignoring", entity);
      return false;
    }

    return true;
  }

  /**
   * Update an existing entity.
   *
   * @param entity the entity to update
   * @param <T>    the entity type
   * @throws EntityNotFoundException if the entity does not yet exist on the
   *                                 ledger
   */
  public <T> void mustUpdate(final T entity) throws EntityNotFoundException {
    assertExists(entity);

    final String key = getCompositeKey(entity);
    final byte[] buffer = entityProvider.toBuffer(entity);
    stub.putState(key, buffer);
  }

  /**
   * Update an entity if it exists.
   *
   * @param entity the entity to update
   * @return {@code true} if an entity was updated, {@code false} otherwise
   * @param <T> the entity type
   */
  public <T> boolean tryUpdate(final T entity) {
    try {
      mustUpdate(entity);
    } catch (EntityNotFoundException e) {
      logger.info("{} does not exist -- ignoring update", entity);
      return false;
    }

    return true;
  }

  /**
   * Delete an existing entity.
   *
   * @param entity the entity to delete
   * @param <T>    the entity type
   * @throws EntityNotFoundException if the entity was not found in the ledger
   */
  public <T> void mustDelete(final T entity) throws EntityNotFoundException {
    assertExists(entity);

    final String key = getCompositeKey(entity);
    stub.delState(key);
  }

  /**
   * Delete an entity if it exists.
   *
   * @param entity the entity to delete
   * @return {@code true} if an entity was deleted, {@code false} otherwise
   * @param <T> the entity type
   */
  public <T> boolean tryDelete(final T entity) {
    try {
      mustDelete(entity);
    } catch (EntityNotFoundException e) {
      logger.info("{} does not exist -- ignoring delete", entity);
      return false;
    }

    return true;
  }

  /**
   * Read an existing entity.
   *
   * @param clazz    the class of the entity
   * @param keyParts the list of primary keys identifying the entity
   * @return the entity read and deserialized from the ledger
   * @param <T> the entity type
   * @throws EntityNotFoundException if an entity with the given primary keys was
   *                                 not found
   */
  public <T> T mustRead(Class<T> clazz, Object... keyParts) throws EntityNotFoundException {
    EntityKeyProvider provider = entityProvider.getKeyProviderForClass(clazz);
    if (provider == null) {
      throw new MissingPrimaryKeysException(
          String.format("%s does not have a key annotation", clazz));
    }
    EntityMeta meta = entityProvider.getMetaDataInventory().getForClass(clazz);
    int keyCount = meta.getPrimaryKeyDescriptor().getAttributeDescriptiors().size();
    if (keyParts.length != keyCount) {
      throw new IllegalArgumentException(
          "The number of key parts provided does not match number of primary keys for "
              + clazz.getName());
    }

    final String key = entityProvider.createCompositeKey(clazz, keyParts);
    final byte[] data = stub.getState(key);

    if (data == null || data.length == 0) {
      throw new EntityNotFoundException(key);
    }

    return entityProvider.fromBuffer(data, clazz);
  }

  /**
   * Read an entity if it exists.
   *
   * @param clazz the class of the entity
   * @param keys  the list of primary keys identifying the entity
   * @return the entity read and deserialized from the ledger if found,
   *         {@code null} otherwise
   * @param <T> the entity type
   */
  public <T> T tryRead(Class<T> clazz, Object... keys) {
    try {
      return mustRead(clazz, keys);
    } catch (EntityNotFoundException e) {
      logger.info("Entity of type {} with keys {} not found -- ignoring", clazz.getName(), keys);
      return null;
    }
  }

  /**
   * Read all entities of a given type.
   *
   * @param clazz the class of the entity
   * @return a list of all entities read (might be empty)
   * @param <T> the entity type
   */
  public <T> List<T> readAll(final Class<T> clazz) {
    final String key = entityProvider.createCompositeKey(clazz);
    Iterator<KeyValue> iterator = stub.getStateByPartialCompositeKey(key).iterator();
    Iterable<KeyValue> iterable = () -> iterator;
    return StreamSupport.stream(iterable.spliterator(), false)
        .map(
            kv -> {
              final byte[] value = kv.getValue();
              logger.debug(
                  "Found value at partial key {}: {} -> {}",
                  key,
                  kv.getKey(),
                  Arrays.toString(value));
              return entityProvider.fromBuffer(value, clazz);
            })
        .collect(Collectors.toList());
  }

  @Loggable(Loggable.DEBUG)
  private boolean keyExists(final String key) {
    final byte[] valueOnLedger = stub.getState(key);
    return valueOnLedger != null && valueOnLedger.length > 0;
  }

  @Loggable(Loggable.DEBUG)
  private <T> boolean exists(final T ent) {
    return keyExists(getCompositeKey(ent));
  }

  @Loggable(Loggable.DEBUG)
  private <T> void assertNotExists(final T ent) throws EntityExistsException {
    if (exists(ent)) {
      throw new EntityExistsException(getCompositeKey(ent));
    }
  }

  @Loggable(Loggable.DEBUG)
  private <T> void assertExists(final T ent) throws EntityNotFoundException {
    if (!exists(ent)) {
      throw new EntityNotFoundException(getCompositeKey(ent));
    }
  }

  private <T> String getCompositeKey(final T ent) {
    EntityKeyProvider keyProvider = entityProvider.getKeyProviderForClass(ent.getClass());
    return keyProvider.getKey(ent);
  }
}
