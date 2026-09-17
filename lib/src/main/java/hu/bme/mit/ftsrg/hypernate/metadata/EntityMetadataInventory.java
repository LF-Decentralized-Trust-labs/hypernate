/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import static java.util.stream.Collectors.joining;

import hu.bme.mit.ftsrg.hypernate.annotations.*;
import hu.bme.mit.ftsrg.hypernate.mappers.ObjectToString;
import hu.bme.mit.ftsrg.hypernate.registry.ConflictingMetadataException;
import hu.bme.mit.ftsrg.hypernate.registry.ConflictingOrderException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingKeysException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingOrderException;
import io.github.classgraph.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UtilityClass
class EntityMetadataInventory {

  private final Logger logger = LoggerFactory.getLogger(EntityMetadataInventory.class);

  private final Map<Class<?>, EntityDescriptor> data = new ConcurrentHashMap<>();
  private final Map<Class<?>, ConflictingMetadataException> conflicts = new ConcurrentHashMap<>();
  private final Map<String, Throwable> failures = new ConcurrentHashMap<>();

  /*
   * Initializes the metadata registry by scanning the classpath for annotated classes.
   * Searches for classes marked with PrimaryKey or KeyClass annotations. For each discovered
   * class, it generates the corresponding EntityDescriptor and stores it in the internal
   * metadata inventory.
   */
  static {
    var loader = EntityMetadataInventory.class.getClassLoader();
    var classGraph =
        new ClassGraph()
            .enableClasspath()
            .enableClassInfo()
            .enableAnnotationInfo()
            .ignoreClassVisibility();
    try (ScanResult result = classGraph.scan()) {
      // Process entity classes explicitly annotated with PrimaryKey
      ClassInfoList primaryKeyedClasses =
          result.getClassesWithAnnotation(PrimaryKey.class).directOnly();
      if (primaryKeyedClasses.isEmpty()) {
        logger.info("Discovered no classes annotated with PrimaryKey");
      } else {
        primaryKeyedClasses.forEach(
            info ->
                register(
                    info,
                    loader,
                    UnaryOperator.identity(),
                    EntityMetadataInventory::generateMetadataFromPrimaryKey));
      }

      // Process discovered key classes
      ClassInfoList keyClassInfo = result.getClassesWithAnnotation(KeyClass.class).directOnly();
      if (keyClassInfo.isEmpty()) {
        logger.info("Discovered no classes annotated with KeyClass");
      } else {
        keyClassInfo.forEach(
            info ->
                register(
                    info,
                    loader,
                    keyClass -> keyClass.getAnnotation(KeyClass.class).value(),
                    EntityMetadataInventory::generateMetadataFromKeyClass));
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
   * @throws ConflictingMetadataException if multiple metadata descriptors were detected for the
   *     class
   * @throws MissingKeysException if a failure has been recorded for the class during scan-time
   */
  public EntityDescriptor getForClass(final Class<?> clazz) {
    final ConflictingMetadataException conflict = conflicts.get(clazz);
    if (conflict != null) {
      throw conflict;
    }

    final Throwable failure = failures.get(clazz.getName());
    if (failure != null) {
      throw new MissingKeysException(
          String.format("Metadata for entity %s could not be built at startup", clazz.getName()),
          failure);
    }

    return data.get(clazz);
  }

  /**
   * Collect the fields in a {@link KeyClass} and sort by their annotated order.
   *
   * @param keyClass the key class to collect from
   * @return an integer-field mapping of the fields that indeed identify key parts
   * @throws MissingOrderException if a field is encountered that has no {@link KeyOrder} annotation
   * @throws ConflictingOrderException if multiple fields have {@link KeyOrder} annotations with the
   *     same integer value
   * @implNote package-private for testing
   */
  Map<Integer, Field> collectKeyFieldsByOrder(final Class<?> keyClass) {
    final TreeMap<Integer, Field> fieldsByOrder = new TreeMap<>();
    for (Field field : keyClass.getDeclaredFields()) {
      // Synthetic and static fields are ignored
      if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
        continue;
      }

      // KeyOrder must be present
      final KeyOrder order = field.getAnnotation(KeyOrder.class);
      if (order == null) {
        throw new MissingOrderException(
            "Field %s of key class %s has no @KeyOrder; every key field needs one so that the composite key order is well defined"
                .formatted(field.getName(), keyClass.getName()));
      }

      // Enforce unique order numbers
      final Field previous = fieldsByOrder.putIfAbsent(order.value(), field);
      if (previous != null) {
        throw new ConflictingOrderException(
            "Key class %s uses @KeyOrder(%d) on more than one field (%s and %s); the resulting key order would be arbitrary"
                .formatted(keyClass.getName(), order.value(), previous.getName(), field.getName()));
      }
    }
    return fieldsByOrder;
  }

  /**
   * Registers an entity descriptor, or records a conflict if the entity already has one.
   *
   * <p>A conflict (more than one source of metadata for the same entity; eg, two {@link KeyClass}es
   * or a {@link KeyClass} pointing at an entity that also carries {@link PrimaryKey}) is recorded
   * rather than thrown: the entity gets no descriptor and {@link #getForClass(Class)} throws the
   * recorded exception for it.
   *
   * @param meta the descriptor to register
   * @implNote package-private for testing
   */
  void add(final EntityDescriptor meta) {
    final Class<?> clazz = meta.clazz();
    final String name = clazz.getName();

    final ConflictingMetadataException earlier = conflicts.get(clazz);
    if (earlier != null) {
      final var extra =
          new ConflictingMetadataException(
              "Yet another source of primary key metadata for entity %s (%s)"
                  .formatted(name, describeAttributes(meta)));
      earlier.addSuppressed(extra);
      logger.error("Entity {} has yet another source of primary key metadata", name, extra);
      return;
    }

    final EntityDescriptor existing = data.remove(clazz);
    if (existing != null) {
      final var e =
          new ConflictingMetadataException(
              "Entity %s has conflicting primary key metadata (%s vs %s); refusing to choose between them. Check for a duplicate @KeyClass or a @PrimaryKey that a @KeyClass also points at."
                  .formatted(name, describeAttributes(existing), describeAttributes(meta)));
      conflicts.put(clazz, e);
      logger.error("Entity {} will be unusable due to conflicting primary key metadata", name, e);
      return;
    }

    data.put(clazz, meta);
  }

  /**
   * Dummy method to call in order to trigger an execution of the static initializer block and the
   * classpath scan.
   */
  void ensureScanned() {}

  /**
   * Record a failure to throw later when the associated entity is used.
   *
   * <p>The main reported failure is the first one; further ones are attached as suppressed
   * exceptions.
   *
   * @param entityName the entity to record the failure for
   * @param failure the failure to later throw
   * @implNote package-private for testing
   */
  void addFailure(final String entityName, final Throwable failure) {
    failures.merge(
        entityName,
        failure,
        (Throwable earlier, Throwable later) -> {
          if (earlier != later) {
            earlier.addSuppressed(later);
          }
          return later;
        });
  }

  /**
   * Runs one class's metadata generation, containing any failure to that class.
   *
   * <p>This runs from a static initializer, so an escaping exception or linkage error (eg, field
   * type missing at runtime) would become an {@link ExceptionInInitializerError} and leave this
   * class permanently unusable for the rest of the JVM's life -- one malformed entity anywhere on
   * the classpath would take down every other one. Instead, the bad class is skipped and reported;
   * looking it up later fails with {@link MissingKeysException}.
   *
   * @param classInfo the class being processed
   * @param loader the class loader to use
   * @param entityOf a function that determines what the relevant entity is called
   * @param generator the generation step to run for it
   */
  private void register(
      final ClassInfo classInfo,
      final ClassLoader loader,
      final UnaryOperator<Class<?>> entityOf,
      final Consumer<Class<?>> generator) {
    String entityName = classInfo.getName();
    try {
      final Class<?> annotatedClass = Class.forName(classInfo.getName(), false, loader);
      entityName = entityOf.apply(annotatedClass).getName();
      generator.accept(annotatedClass);
    } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
      logger.error(
          "Failed to build entity metadata for {} -- skipping it; using this entity will fail",
          classInfo.getName(),
          e);
      addFailure(entityName, e);
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

  private Optional<String> declaredTypeOf(final Class<?> entityClass) {
    EntityType annot = entityClass.getAnnotation(EntityType.class);
    if (annot == null) {
      return Optional.empty();
    }

    final String value = annot.value();
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          String.format(
              "The @EntityType annotation on class %s has an empty or blank value",
              entityClass.getName()));
    }

    return Optional.of(value);
  }

  /**
   * Generates and registers metadata for a class annotated with {@link PrimaryKey}.
   *
   * <p>This method extracts attribute and mapper information directly from the {@code @PrimaryKey}
   * annotation's value array.
   *
   * @param clazz the annotated class
   */
  private void generateMetadataFromPrimaryKey(final Class<?> clazz) {
    final PrimaryKey pk = clazz.getAnnotation(PrimaryKey.class);

    final String declaredBy = "@PrimaryKey on " + clazz.getName();
    final List<AttributeDescriptor> attributes =
        Arrays.stream(pk.value())
            .map(
                i -> new AttributeDescriptor(resolveField(clazz, i.name(), declaredBy), i.mapper()))
            .toList();

    var pkDesc = new PrimaryKeyDescriptor(attributes);
    final String type = declaredTypeOf(clazz).orElse(clazz.getName());
    add(new EntityDescriptor(clazz, pkDesc, type));
  }

  private void assertAssignable(final Field to, final Field from) {
    final Class<?> toType = box(to.getType());
    final Class<?> fromType = box(from.getType());
    if (!toType.isAssignableFrom(fromType)) {
      throw new IllegalArgumentException(
          "Field %s in type %s is not assignable from field %s in type %s"
              .formatted(
                  to.getName(), toType.getTypeName(), from.getName(), fromType.getTypeName()));
    }
  }

  private Class<?> box(final Class<?> type) {
    return type.isPrimitive() ? MethodType.methodType(type).wrap().returnType() : type;
  }

  /**
   * Generates and registers metadata based on a {@link KeyClass} annotation.
   *
   * <p>Unlike {@code PrimaryKey}, this method treats the annotated class as a template for a target
   * entity. It uses reflection to scan the fields of the annotated class to build the primary key
   * descriptor for the referenced entity class.
   *
   * @param keyClass the class containing the {@code @KeyClass} annotation
   */
  private void generateMetadataFromKeyClass(final Class<?> keyClass) {
    final Class<?> pointedClass = keyClass.getAnnotation(KeyClass.class).value();

    final Map<Integer, Field> fieldsByOrder = collectKeyFieldsByOrder(keyClass);

    // The key class's own fields are only a source of names, order and mapper choice; the fields
    // the key is actually built from live on the entity.
    final String declaredBy = "key class " + keyClass.getName();
    final List<AttributeDescriptor> attributes =
        fieldsByOrder.values().stream()
            .map(
                (Field keyClassField) -> {
                  final Field pointedClassField =
                      resolveField(pointedClass, keyClassField.getName(), declaredBy);
                  assertAssignable(keyClassField, pointedClassField);
                  return new AttributeDescriptor(
                      pointedClassField,
                      keyClassField.isAnnotationPresent(MapperInfo.class)
                          ? keyClassField.getAnnotation(MapperInfo.class).value()
                          : ObjectToString.class);
                })
            .toList();

    var pkDesc = new PrimaryKeyDescriptor(attributes);

    // Check for @EntityType conflicts (both on key class and pointed entity class)
    final Optional<String> keyClassType = declaredTypeOf(keyClass);
    final Optional<String> pointedClassType = declaredTypeOf(pointedClass);
    if (keyClassType.isPresent()
        && pointedClassType.isPresent()
        && !keyClassType.equals(pointedClassType)) {
      throw new ConflictingMetadataException(
          "Key class %s declares @EntityType(\"%s\"), but the entity %s it points at declares @EntityType(\"%s\"); refusing to choose between them"
              .formatted(
                  keyClass.getName(),
                  keyClassType.get(),
                  pointedClass.getName(),
                  pointedClassType.get()));
    }

    final String type = keyClassType.or(() -> pointedClassType).orElse(pointedClass.getName());
    add(new EntityDescriptor(pointedClass, pkDesc, type));
  }
}
