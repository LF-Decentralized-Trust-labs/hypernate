/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import static java.util.stream.Collectors.joining;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.registry.KeyArityException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingKeysException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Loggable(Loggable.DEBUG)
public class EntityMetadataProvider {

  private static final Logger logger = LoggerFactory.getLogger(EntityMetadataProvider.class);

  /**
   * Mapper instances, shared process-wide by kind.
   *
   * <p>{@link AttributeMapper}s are required to be stateless, so one instance per kind is enough no
   * matter how many entities and attributes use it.
   */
  private static final Map<Class<? extends AttributeMapper>, AttributeMapper> MAPPERS =
      new ConcurrentHashMap<>();

  /**
   * Cache of the resolved key providers.
   *
   * <p>Resolving one means reflective field lookup and mapper instantiation, so instances of this
   * class are meant to be shared and long-lived. Concurrent because Fabric dispatches transactions
   * on several threads.
   */
  private final Map<Class<?>, EntityKeyProvider> keyProviderForClass = new ConcurrentHashMap<>();

  /**
   * Retrieves or creates an {@link EntityKeyProvider} for the specified class of an entity. The
   * provider is cached for subsequent use.
   *
   * @param entityClass the class of the entity
   * @return the key provider associated with the given class
   */
  public EntityKeyProvider keyProviderFor(final Class<?> entityClass) {
    return keyProviderForClass.computeIfAbsent(entityClass, this::createEntityKeyProvider);
  }

  /**
   * Retrieves or creates an {@link EntityKeyProvider} for specified entity. The provider is cached
   * for subsequent use.
   *
   * @param entity the entity
   * @return the key provider associated with the given class
   */
  public EntityKeyProvider keyProviderFor(final Object entity) {
    return keyProviderFor(entity.getClass());
  }

  public String typeStringFor(final Class<?> entityClass) {
    EntityType entityTypeAnnot = entityClass.getAnnotation(EntityType.class);
    if (entityTypeAnnot == null) {
      return entityClass.getName();
    }

    final String value = entityTypeAnnot.value();
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          String.format(
              "The @EntityType annotation on class %s has an empty or blank value",
              entityClass.getName()));
    }

    return value;
  }

  /**
   * Returns the shared instance of a mapper kind, creating it on first use.
   *
   * <p>Mappers are stateless, so one instance per kind serves every attribute of every entity.
   *
   * @param mapperClass the kind of mapper to instantiate
   * @return the shared mapper instance
   * @throws MissingKeysException if the mapper has no usable no-argument constructor
   */
  private static AttributeMapper mapperFor(final Class<? extends AttributeMapper> mapperClass) {
    return MAPPERS.computeIfAbsent(
        mapperClass,
        c -> {
          try {
            return c.getDeclaredConstructor().newInstance();
          } catch (ReflectiveOperationException e) {
            logger.error("Failed to instantiate mapper: {}", c.getName(), e);
            throw new MissingKeysException("Error instantiating mapper: " + c.getName(), e);
          }
        });
  }

  /**
   * Generates a lambda which builds a CompositeKey for a given class instance
   *
   * <p>Using reflection we access the Field values which are given as primary keys, and with our
   * mappers instances we map the values and with these we build the Composite Key.
   *
   * @param entityClass the class of the entity
   * @return a lambda that creates a CompositeKey for an object instance
   */
  private EntityKeyProvider createEntityKeyProvider(Class<?> entityClass) {
    EntityDescriptor entityDesc = EntityMetadataInventory.getForClass(entityClass);
    if (entityDesc == null) {
      throw new MissingKeysException(
          "Entity metadata not found for class: " + entityClass.getName());
    }

    PrimaryKeyDescriptor pkDesc = entityDesc.primaryKey();
    if (pkDesc.attributes().isEmpty()) {
      throw new MissingKeysException(
          "No primary key descriptors found for class: " + entityClass.getName());
    }

    List<FieldWithMapper> fields =
        pkDesc.attributes().stream()
            .map(desc -> new FieldWithMapper(desc.field(), mapperFor(desc.mapper())))
            .toList();

    final String type = typeStringFor(entityClass);
    return new EntityKeyProvider() {
      @Override
      public String getKey(final Object entity) {
        return getKeyForParts(
            fields.stream()
                .map(
                    f -> {
                      try {
                        return f.field.get(entity);
                      } catch (IllegalAccessException e) {
                        throw new MissingKeysException(
                            "Could not access primary key field %s on %s"
                                .formatted(f.field.getName(), entityClass.getName()),
                            e);
                      }
                    })
                .toArray());
      }

      @Override
      public String getKeyForParts(final Object... keyParts) {
        if (keyParts.length != fields.size()) {
          throw new KeyArityException(
              "Entity %s has %d primary key attribute(s) (%s) but %d key part(s) were supplied"
                  .formatted(
                      entityClass.getName(),
                      fields.size(),
                      fields.stream().map(f -> f.field.getName()).collect(joining(", ")),
                      keyParts.length));
        }
        List<String> attribs =
            IntStream.range(0, fields.size())
                .mapToObj(i -> fields.get(i).mapper.apply(keyParts[i]))
                .toList();

        return new CompositeKey(type, attribs).toString();
      }

      @Override
      public String getType() {
        return type;
      }
    };
  }

  private record FieldWithMapper(Field field, AttributeMapper mapper) {}
}
