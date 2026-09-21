/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import static java.util.stream.Collectors.*;

import hu.bme.mit.ftsrg.hypernate.annotations.KeyClass;
import hu.bme.mit.ftsrg.hypernate.annotations.KeyOrder;
import hu.bme.mit.ftsrg.hypernate.annotations.MapperInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.mappers.ObjectToString;
import hu.bme.mit.ftsrg.hypernate.registry.ConflictingMetadataException;
import hu.bme.mit.ftsrg.hypernate.registry.ConflictingOrderException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingKeysException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingOrderException;
import io.github.classgraph.*;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UtilityClass
class EntityMetadataInventory {

  private final Logger logger = LoggerFactory.getLogger(EntityMetadataInventory.class);

  private final Map<Class<?>, EntityDescriptor> data = new ConcurrentHashMap<>();

  /*
   * Initializes the metadata registry by scanning the classpath for annotated classes. Searches for
   * classes marked with {@link PrimaryKey} or {@link KeyClass} annotations. For each discovered
   * class, it generates the corresponding {@link EntityMetadata} and stores it in the internal
   * metadata inventory.
   */
  static {
    var classGraph =
        new ClassGraph()
            .enableClassInfo()
            .enableExternalClasses()
            .ignoreClassVisibility()
            .enableAnnotationInfo();
    try (ScanResult result = classGraph.scan()) {
      // Process entity classes explicitly annotated with PrimaryKey
      ClassInfoList primaryKeyedClasses = result.getClassesWithAnnotation(PrimaryKey.class);
      if (primaryKeyedClasses.isEmpty()) {
        logger.info("Discovered no classes annotated with PrimaryKey");
      } else {
        primaryKeyedClasses.forEach(
            info -> register(info, EntityMetadataInventory::generateMetadataFromPrimaryKey));
      }

      // Process discovered key classes
      ClassInfoList keyClassInfo = result.getClassesWithAnnotation(KeyClass.class);
      if (keyClassInfo.isEmpty()) {
        logger.info("Discovered no classes annotated with KeyClass");
      } else {
        keyClassInfo.forEach(
            info -> register(info, EntityMetadataInventory::generateMetadataFromKeyClass));
      }
    } catch (ClassGraphException e) {
      logger.error("Failed to scan classpath; cannot build metadata inventory", e);
    }
  }

  /**
   * Return the entity descriptor for an entity.
   *
   * @param clazz the class whose entity descriptor is needed
   * @return the cached entity descriptor or <code>null</code> if it could not be found
   */
  public EntityDescriptor getForClass(final Class<?> clazz) {
    return data.get(clazz);
  }

  /**
   * Runs one class's metadata generation, containing any failure to that class.
   *
   * <p>This runs from a static initializer, so an escaping exception would become an {@link
   * ExceptionInInitializerError} and leave this class permanently unusable for the rest of the
   * JVM's life -- one malformed entity anywhere on the classpath would take down every other one.
   * Instead, the bad class is skipped and reported; looking it up later fails with the usual
   * "metadata not found" error.
   *
   * @param classInfo the class being processed
   * @param generator the generation step to run for it
   */
  private void register(final ClassInfo classInfo, final Consumer<ClassInfo> generator) {
    try {
      generator.accept(classInfo);
    } catch (RuntimeException e) {
      logger.error(
          "Failed to build entity metadata for {} -- skipping it; using this entity will fail",
          classInfo.getName(),
          e);
    }
  }

  /**
   * Registers an entity descriptor, rejecting a second one for the same entity.
   *
   * <p>Two {@link KeyClass}es aimed at the same entity, or an entity carrying {@link PrimaryKey}
   * that a key class also points at, would otherwise silently resolve to whichever the classpath
   * scan happened to reach last.
   *
   * @param meta the descriptor to register
   * @throws ConflictingMetadataException if this entity already has a descriptor
   */
  private void add(final EntityDescriptor meta) {
    final EntityDescriptor existing = data.putIfAbsent(meta.clazz(), meta);
    if (existing != null) {
      throw new ConflictingMetadataException(
          "Entity %s already has primary key metadata (%s); refusing to replace it with (%s). Check for a duplicate @KeyClass or a @PrimaryKey that a @KeyClass also points at."
              .formatted(
                  meta.clazz().getName(), describeAttributes(existing), describeAttributes(meta)));
    }
  }

  /**
   * Resolves a primary key field on an entity class and makes it readable.
   *
   * <p>Walks up the class hierarchy so that key fields declared on a base entity class are found
   * too. Resolving here rather than on first use means a key naming a field that does not exist
   * fails while the offending class is still in hand, so the error can name it.
   *
   * @param entityClass the entity class to search
   * @param fieldName the field name to look for
   * @param declaredBy human-readable description of what declared this key field, for errors
   * @return the resolved, readable field
   * @throws MissingKeysException if no class in the hierarchy declares such a field, or it cannot
   *     be made accessible
   */
  private Field resolveField(
      @NonNull final Class<?> entityClass, final String fieldName, final String declaredBy) {
    for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
      final Field field;
      try {
        field = c.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        continue; // Not declared here; keep walking up.
      }

      try {
        field.setAccessible(true);
      } catch (RuntimeException e) {
        throw new MissingKeysException(
            "Primary key field %s of entity %s (declared by %s) could not be made accessible"
                .formatted(fieldName, entityClass.getName(), declaredBy),
            e);
      }

      return field;
    }

    throw new MissingKeysException(
        "%s declares key field %s, but entity %s has no such field (nor do any of its superclasses)"
            .formatted(declaredBy, fieldName, entityClass.getName()));
  }

  private String describeAttributes(final EntityDescriptor meta) {
    return meta.primaryKey().attributes().stream()
        .map(a -> a.field().getName())
        .collect(joining(", "));
  }

  /**
   * Generates and registers metadata for a class annotated with {@link PrimaryKey}.
   *
   * <p>This method extracts attribute and mapper information directly from the {@code @PrimaryKey}
   * annotation's value array.
   *
   * @param primaryKeyedClassInfo the {@link ClassInfo} of the annotated class
   */
  private void generateMetadataFromPrimaryKey(final ClassInfo primaryKeyedClassInfo) {
    final Class<?> clazz = primaryKeyedClassInfo.loadClass();
    final PrimaryKey pk = clazz.getAnnotation(PrimaryKey.class);

    final String declaredBy = "@PrimaryKey on " + clazz.getName();
    final List<AttributeDescriptor> attributes =
        Arrays.stream(pk.value())
            .map(
                i -> new AttributeDescriptor(resolveField(clazz, i.name(), declaredBy), i.mapper()))
            .toList();

    var pkDesc = new PrimaryKeyDescriptor(attributes);
    add(new EntityDescriptor(clazz, pkDesc));
  }

  /**
   * Generates and registers metadata based on a {@link KeyClass} annotation.
   *
   * <p>Unlike {@code PrimaryKey}, this method treats the annotated class as a template for a target
   * entity. It uses reflection to scan the fields of the annotated class to build the primary key
   * descriptor for the referenced entity class.
   *
   * @param keyClassInfo the {@link ClassInfo} of the class containing the {@code @KeyClass}
   *     annotation
   */
  private void generateMetadataFromKeyClass(final ClassInfo keyClassInfo) {
    Class<?> keyClass = keyClassInfo.loadClass();
    Class<?> pointedClass = keyClass.getAnnotation(KeyClass.class).value();

    Supplier<Stream<Field>> keyFieldsSupp = () -> Arrays.stream(keyClass.getDeclaredFields());

    keyFieldsSupp
        .get()
        .filter(f -> !f.isAnnotationPresent(KeyOrder.class))
        .findFirst()
        .ifPresent(
            f -> {
              throw new MissingOrderException(
                  "Field %s of key class %s has no @KeyOrder; every key field needs one so that the composite key order is well defined"
                      .formatted(f.getName(), keyClass.getName()));
            });

    final Map<Integer, List<String>> fieldsByOrder =
        keyFieldsSupp
            .get()
            .collect(
                groupingBy(
                    f -> f.getAnnotation(KeyOrder.class).value(),
                    mapping(Field::getName, toList())));
    fieldsByOrder.entrySet().stream()
        .filter(e -> e.getValue().size() > 1)
        .findFirst()
        .ifPresent(
            e -> {
              throw new ConflictingOrderException(
                  "Key class %s uses @KeyOrder(%d) on more than one field (%s); the resulting key order would be arbitrary"
                      .formatted(keyClass.getName(), e.getKey(), String.join(", ", e.getValue())));
            });

    // The key class's own fields are only a source of names, order and mapper choice; the fields
    // the key is actually built from live on the entity.
    final String declaredBy = "key class " + keyClass.getName();
    final List<AttributeDescriptor> attributes =
        keyFieldsSupp
            .get()
            .sorted(Comparator.comparingInt(f -> f.getAnnotation(KeyOrder.class).value()))
            .map(
                field ->
                    new AttributeDescriptor(
                        resolveField(pointedClass, field.getName(), declaredBy),
                        field.isAnnotationPresent(MapperInfo.class)
                            ? field.getAnnotation(MapperInfo.class).value()
                            : ObjectToString.class))
            .toList();

    var pkDesc = new PrimaryKeyDescriptor(attributes);
    add(new EntityDescriptor(pointedClass, pkDesc));
  }
}
