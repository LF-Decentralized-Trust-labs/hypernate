/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.registry.MissingKeysException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Loggable(Loggable.DEBUG)
public class EntityMetadataProvider {
  private static final Logger logger = LoggerFactory.getLogger(EntityMetadataProvider.class);
  private EntityMetadataInventory metaInventory = new EntityMetadataInventory();
  private Map<Class<?>, EntityKeyProvider> keyProviders = new HashMap<>();

  <T> String getType(final T entity) {
    return getType(entity.getClass());
  }

  <T> String getType(final Class<T> clazz) {
    final EntityType annot = clazz.getAnnotation(EntityType.class);
    if (annot == null) {
      return clazz.getName();
    }

    final String value = annot.value();
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          String.format(
              "The @EntityType annotation on class %s has an empty or blank value",
              clazz.getName()));
    }
    return value;
  }

  <T> String[] mapKeyPartsToString(final T entity, final Object... keyParts) {
    return mapKeyPartsToString(entity.getClass(), keyParts);
  }

  <T> String[] mapKeyPartsToString(final Class<T> clazz, final Object... keyParts) {
    EntityMeta em = metaInventory.getForClass(clazz);

    if (em == null) {
      throw new MissingKeysException("Entity metadata not found for class: " + clazz.getName());
    }

    List<Field> fields = new ArrayList<>();
    List<AttributeMapper> mappers = new ArrayList<>();
    PrimaryKeyDescriptor pk = em.getPrimaryKeyDescriptor();
    if (pk == null
        || pk.getAttributeDescriptors() == null
        || pk.getAttributeDescriptors().isEmpty()) {
      throw new MissingKeysException(
          "No primary key descriptors found for class: " + clazz.getName());
    }
    List<AttributeDescriptor> pkAttributeDescriptors = pk.getAttributeDescriptors();
    for (AttributeDescriptor descriptor : pkAttributeDescriptors) {
      try {
        Field field = clazz.getDeclaredField(descriptor.getAttrFieldName());
        field.setAccessible(true);
        fields.add(field);
      } catch (Exception e) {
        throw new MissingKeysException("Error accessing fields for class: " + clazz.getName(), e);
      }
      if (descriptor.getAttributeMapperDescriptor() == null) {
        mappers.add(null);
        continue;
      }
      String mapperName = descriptor.getAttributeMapperDescriptor().getMapperName();
      try {
        Class<?> mapperClass = Class.forName(mapperName);
        AttributeMapper mapper =
            (AttributeMapper) mapperClass.getDeclaredConstructor().newInstance();
        mappers.add(mapper);
      } catch (ReflectiveOperationException e) {
        logger.error("Failed to instantiate mapper: {}", mapperName, e);
        throw new MissingKeysException("Error instantiating mapper: " + mapperName, e);
      }
    }
    List<String> stringKeyParts = new ArrayList<>();
    for (int i = 0; i < fields.size(); i++) {
      String value = keyParts[i].toString();
      if (mappers.get(i) != null) {
        stringKeyParts.add(mappers.get(i).apply(value));
      } else {
        stringKeyParts.add(value);
      }
    }
    return stringKeyParts.toArray(String[]::new);
  }

  /**
   * Serializes the given entity into a JSON byte array using UTF-8 encoding.
   *
   * @param entity the entity object to serialize
   * @param <T> the type of the entity
   * @return a byte array representation of the serialized entity
   */
  public <T> byte[] toBuffer(final T entity) {
    return toJson(entity).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Deserializes a JSON byte array into an entity object of the specified class.
   *
   * @param buffer the byte array containing the JSON data in UTF-8 encoding
   * @param clazz the class of the entity to instantiate
   * @param <T> the type of the entity
   * @return the deserialized entity object
   */
  public <T> T fromBuffer(final byte[] buffer, final Class<T> clazz) {
    final String json = new String(buffer, StandardCharsets.UTF_8);
    return JSON.deserialize(json, clazz);
  }

  <T> String toJson(final T entity) {
    return JSON.serialize(entity);
  }

  /**
   * Retrieves or creates an {@link EntityKeyProvider} for the specified class. The provider is
   * cached for subsequent use.
   *
   * @param clazz the class of the entity
   * @return the key provider associated with the given class
   */
  public EntityKeyProvider getKeyProviderForClass(Class<?> clazz) {
    if (!keyProviders.containsKey(clazz)) {
      EntityKeyProvider provider = createEntityKeyProvider(clazz);
      keyProviders.put(clazz, provider);
    }
    return keyProviders.get(clazz);
  }

  /**
   * Generates a lambda which builds a CompositeKey for a given class instance
   *
   * <p>Using reflection we access the Field values which are given as primary keys, and with our
   * mappers instances we map the values and with these we build the Composite Key.
   *
   * @param clazz the class of the entity
   * @return a lambda that creates a CompositeKey for an object instance
   */
  private EntityKeyProvider createEntityKeyProvider(Class<?> clazz) {
    EntityMeta em = metaInventory.getForClass(clazz);

    if (em == null) {
      throw new MissingKeysException("Entity metadata not found for class: " + clazz.getName());
    }

    List<Field> fields = new ArrayList<>();
    List<AttributeMapper> mappers = new ArrayList<>();
    PrimaryKeyDescriptor pk = em.getPrimaryKeyDescriptor();
    if (pk == null
        || pk.getAttributeDescriptors() == null
        || pk.getAttributeDescriptors().isEmpty()) {
      throw new MissingKeysException(
          "No primary key descriptors found for class: " + clazz.getName());
    }
    List<AttributeDescriptor> pkAttributeDescriptors = pk.getAttributeDescriptors();
    for (AttributeDescriptor descriptor : pkAttributeDescriptors) {
      try {
        Field field = clazz.getDeclaredField(descriptor.getAttrFieldName());
        field.setAccessible(true);
        fields.add(field);
      } catch (Exception e) {
        throw new MissingKeysException("Error accessing fields for class: " + clazz.getName(), e);
      }
      if (descriptor.getAttributeMapperDescriptor() == null) {
        mappers.add(null);
        continue;
      }
      String mapperName = descriptor.getAttributeMapperDescriptor().getMapperName();
      try {
        Class<?> mapperClass = Class.forName(mapperName);
        AttributeMapper mapper =
            (AttributeMapper) mapperClass.getDeclaredConstructor().newInstance();
        mappers.add(mapper);
      } catch (ReflectiveOperationException e) {
        logger.error("Failed to instantiate mapper: {}", mapperName, e);
        throw new MissingKeysException("Error instantiating mapper: " + mapperName, e);
      }
    }

    return (Object entity) -> {
      List<String> keyParts = new ArrayList<>();
      for (int i = 0; i < fields.size(); i++) {
        try {
          String value = fields.get(i).get(entity).toString();
          if (mappers.get(i) != null) {
            keyParts.add(mappers.get(i).apply(value));
          } else {
            keyParts.add(value);
          }
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Could not access field value on entity", e);
        }
      }
      return new CompositeKey(clazz.getName(), keyParts).toString();
    };
  }

  /**
   * Creates a base composite key for a given entity class based on its type name. This is typically
   * used for partial key queries to retrieve all entities of a type.
   *
   * @param clazz the class of the entity
   * @return a string representation of the composite key
   */
  public String createCompositeKey(final Class<?> clazz) {
    return new CompositeKey(getType(clazz)).toString();
  }

  /**
   * Creates a complete composite key for a given entity class and its primary key parts.
   *
   * @param clazz the class of the entity
   * @param keyParts the primary key parts (values) identifying a specific entity
   * @return a string representation of the composite key
   */
  public String createCompositeKey(Class<?> clazz, Object... keyParts) {
    return new CompositeKey(getType(clazz), mapKeyPartsToString(clazz, keyParts)).toString();
  }

  /**
   * Retrieves the inventory containing metadata for all registered entities.
   *
   * @return the {@link EntityMetadataInventory} instance
   */
  public EntityMetadataInventory getMetaDataInventory() {
    return metaInventory;
  }
}
