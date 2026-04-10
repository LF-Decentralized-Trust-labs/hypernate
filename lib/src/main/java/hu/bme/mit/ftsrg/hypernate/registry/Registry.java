/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import lombok.experimental.UtilityClass;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Loggable(Loggable.DEBUG)
public class Registry {

  private static final Logger logger = LoggerFactory.getLogger(Registry.class);

  private final ChaincodeStub stub;

  public Registry(final ChaincodeStub stub) {
    this.stub = stub;
  }

  /**
   * Create a new entity.
   *
   * @param entity the entity to create
   * @param <T> the entity type
   * @throws EntityExistsException if the entity already exists in the ledger
   */
  public <T> void mustCreate(final T entity) throws EntityExistsException {
    assertNotExists(entity);

    final String key = getCompositeKey(entity);
    final byte[] buffer = EntityUtil.toBuffer(entity);
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
   * @param <T> the entity type
   * @throws EntityNotFoundException if the entity does not yet exist on the ledger
   */
  public <T> void mustUpdate(final T entity) throws EntityNotFoundException {
    assertExists(entity);

    final String key = getCompositeKey(entity);
    final byte[] buffer = EntityUtil.toBuffer(entity);
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
   * @param <T> the entity type
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
   * @param clazz the class of the entity
   * @param keyParts the list of primary keys identifying the entity
   * @return the entity read and deserialized from the ledger
   * @param <T> the entity type
   * @throws EntityNotFoundException if an entity with the given primary keys was not found
   */
  public <T> T mustRead(Class<T> clazz, Object... keyParts) throws EntityNotFoundException {
    int primaryKeyCount = EntityUtil.getPrimaryKeyCount(clazz);
    if (primaryKeyCount == 0) {
      throw new MissingPrimaryKeysException(
          String.format("%s does not have a primary key annotation", clazz));
    }

    if (keyParts.length != primaryKeyCount) {
      throw new IllegalArgumentException(
          "The number of key parts provided does not match number of primary keys for "
              + clazz.getName());
    }

    final String key =
        stub.createCompositeKey(
                EntityUtil.getType(clazz), EntityUtil.mapKeyPartsToString(clazz, keyParts))
            .toString();
    final byte[] data = stub.getState(key);

    if (data == null || data.length == 0) {
      throw new EntityNotFoundException(key);
    }

    return EntityUtil.fromBuffer(data, clazz);
  }

  /**
   * Read an entity if it exists.
   *
   * @param clazz the class of the entity
   * @param keys the list of primary keys identifying the entity
   * @return the entity read and deserialized from the ledger if found, {@code null} otherwise
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
    final String key = stub.createCompositeKey(EntityUtil.getType(clazz)).toString();
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
              return EntityUtil.fromBuffer(value, clazz);
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
    return stub.createCompositeKey(EntityUtil.getType(ent), EntityUtil.getPrimaryKeys(ent))
        .toString();
  }

  @UtilityClass
  private class EntityUtil {

    private final Logger logger = LoggerFactory.getLogger(EntityUtil.class);

    <T> String getType(final T entity) {
      return getType(entity.getClass());
    }

    <T> String getType(final Class<T> clazz) {
      return clazz.getName().toUpperCase();
    }

    <T> int getPrimaryKeyCount(final Class<T> clazz) {
      return clazz.getAnnotation(PrimaryKey.class) != null
          ? clazz.getAnnotation(PrimaryKey.class).value().length
          : 0;
    }

    <T> String[] getPrimaryKeys(final T entity) {
      return Arrays.stream(getPrimaryKeyAnnot(entity.getClass()).value())
          .map(
              attrInfo -> {
                logger.debug("Processing primary key attribute {}", attrInfo.name());
                final Object value = getFieldValueForAttr(entity, attrInfo);
                final String mappedKey = applyAttrMapper(attrInfo, value);
                logger.debug(
                    "Result of primary key mapping for attribute {} is {}",
                    attrInfo.name(),
                    mappedKey);
                return mappedKey;
              })
          .toArray(String[]::new);
    }

    <T> String[] mapKeyPartsToString(final T entity, final Object... keyParts) {
      return mapKeyPartsToString(entity.getClass(), keyParts);
    }

    <T> String[] mapKeyPartsToString(final Class<T> clazz, final Object... keyParts) {
      final AttributeInfo[] attrInfos = getPrimaryKeyAnnot(clazz).value();
      return IntStream.range(0, Math.min(attrInfos.length, keyParts.length))
          .mapToObj(i -> applyAttrMapper(attrInfos[i], keyParts[i]))
          .toArray(String[]::new);
    }

    <T> byte[] toBuffer(final T entity) {
      return toJson(entity).getBytes(StandardCharsets.UTF_8);
    }

    <T> T fromBuffer(final byte[] buffer, final Class<T> clazz) {
      final String json = new String(buffer, StandardCharsets.UTF_8);
      logger.debug("Parsing entity from JSON: {}", json);
      return JSON.deserialize(json, clazz);
    }

    <T> String toJson(final T entity) {
      return JSON.serialize(entity);
    }

    private <T> PrimaryKey getPrimaryKeyAnnot(final Class<T> clazz) {
      final PrimaryKey pk = clazz.getAnnotation(PrimaryKey.class);
      if (pk == null) {
        throw new MissingPrimaryKeysException(
            String.format("%s does not have a primary key annotation", clazz));
      }

      return pk;
    }

    private String applyAttrMapper(final AttributeInfo attrInfo, final Object keyPart) {
      Class<? extends Function<Object, String>> mapperClass = attrInfo.mapper();
      Constructor<? extends Function<Object, String>> mapperCtor;
      try {
        mapperCtor = mapperClass.getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        logger.error("Could not find no-arg constructor for mapper {}", mapperClass.getName());
        throw new RuntimeException(e);
      }

      Function<Object, String> mapper;
      try {
        mapper = mapperCtor.newInstance();
      } catch (InstantiationException e) {
        logger.error("Failed to instantiate mapper {}", mapperClass.getName());
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        logger.error("Could not access constructor for mapper {}", mapperClass.getName());
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        logger.error(
            "An exception was thrown by the constructor of mapper {}", mapperClass.getName());
        throw new RuntimeException(e);
      }
      logger.trace(
          "Successfully instantiated mapper of type {} for primary key attribute {}",
          mapperClass.getName(),
          attrInfo.name());

      return mapper.apply(keyPart);
    }

    private <T> Object getFieldValueForAttr(final T ent, final AttributeInfo attrInfo) {
      final Field field;
      try {
        field = ent.getClass().getDeclaredField(attrInfo.name());
      } catch (NoSuchFieldException e) {
        logger.error(
            "Could not find field {} in class {}", attrInfo.name(), ent.getClass().getName());
        throw new RuntimeException(e);
      }
      field.setAccessible(true);
      logger.trace("Found field for primary key attribute {}", attrInfo.name());

      final Object value;
      try {
        value = field.get(ent);
      } catch (IllegalAccessException e) {
        logger.error(
            "Could not access field {} in class {}", field.getName(), ent.getClass().getName());
        throw new RuntimeException(e);
      }

      return value;
    }
  }
}
