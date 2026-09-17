/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hu.bme.mit.ftsrg.hypernate.annotations.KeyClass;
import hu.bme.mit.ftsrg.hypernate.annotations.KeyOrder;
import hu.bme.mit.ftsrg.hypernate.mappers.ObjectToString;
import hu.bme.mit.ftsrg.hypernate.registry.ConflictingMetadataException;
import hu.bme.mit.ftsrg.hypernate.registry.ConflictingOrderException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingKeysException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingOrderException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class EntityMetadataInventoryTest {

  /*
   * The key classes here deliberately carry no @KeyClass: the inventory scans the classpath in its
   * static initializer, and a malformed key class there would be logged as a failure on every test
   * run. collectKeyFieldsByOrder does not need the annotation, so these are called directly.
   */
  @Nested
  class Collecting_key_fields {

    @Test
    void given_fields_declared_out_of_order_then_returns_them_sorted_by_key_order() {
      final Map<Integer, Field> byOrder =
          EntityMetadataInventory.collectKeyFieldsByOrder(OutOfOrderKey.class);

      assertEquals(List.of(1, 2), List.copyOf(byOrder.keySet()));
      assertEquals(List.of("first", "second"), fieldNames(byOrder));
    }

    @Test
    void given_field_without_key_order_then_throws_missing_order_exception() {
      final MissingOrderException e =
          assertThrows(
              MissingOrderException.class,
              () -> EntityMetadataInventory.collectKeyFieldsByOrder(KeyWithoutOrder.class));

      assertTrue(e.getMessage().contains("unordered"), e.getMessage());
    }

    @Test
    void given_two_fields_with_same_key_order_then_throws_conflicting_order_exception() {
      final ConflictingOrderException e =
          assertThrows(
              ConflictingOrderException.class,
              () -> EntityMetadataInventory.collectKeyFieldsByOrder(KeyWithDuplicateOrder.class));

      assertTrue(e.getMessage().contains("alpha"), e.getMessage());
      assertTrue(e.getMessage().contains("beta"), e.getMessage());
    }

    @Test
    void given_static_field_then_ignores_it() {
      final Map<Integer, Field> byOrder =
          EntityMetadataInventory.collectKeyFieldsByOrder(KeyWithStaticField.class);

      assertEquals(List.of("id"), fieldNames(byOrder));
    }

    @Test
    void given_synthetic_field_then_ignores_it() {
      assumeTrue(
          Arrays.stream(KeyWithSyntheticField.class.getDeclaredFields())
              .anyMatch(Field::isSynthetic),
          "compiler emitted no synthetic field for the inner class");

      final Map<Integer, Field> byOrder =
          EntityMetadataInventory.collectKeyFieldsByOrder(KeyWithSyntheticField.class);

      assertEquals(List.of("id"), fieldNames(byOrder));
    }

    private List<String> fieldNames(final Map<Integer, Field> byOrder) {
      return byOrder.values().stream().map(Field::getName).toList();
    }

    private static class OutOfOrderKey {

      @KeyOrder(2)
      String second;

      @KeyOrder(1)
      String first;
    }

    private static class KeyWithoutOrder {

      @KeyOrder(1)
      String ordered;

      String unordered;
    }

    private static class KeyWithDuplicateOrder {

      @KeyOrder(1)
      String alpha;

      @KeyOrder(1)
      String beta;
    }

    private static class KeyWithStaticField {

      static final String CONSTANT = "not a key field";

      @KeyOrder(1)
      String id;
    }

    // Non-static on purpose: javac gives inner classes a synthetic this$0 field.
    private class KeyWithSyntheticField {
      @KeyOrder(1)
      String id;
    }
  }

  @Nested
  class Registering_key_classes {

    @Test
    void given_key_class_with_static_field_then_registers_entity_fields_in_key_order() {
      final EntityDescriptor descriptor = EntityMetadataInventory.getForClass(Warehouse.class);

      assertNotNull(descriptor, "Warehouse should have been registered via WarehouseKey");
      final List<AttributeDescriptor> attributes = descriptor.primaryKey().attributes();
      assertEquals(
          List.of("locationCode", "localId"),
          attributes.stream().map(a -> a.field().getName()).toList());
      attributes.forEach(
          a -> {
            assertEquals(Warehouse.class, a.field().getDeclaringClass());
            assertEquals(ObjectToString.class, a.mapper());
          });
    }

    @Test
    void given_malformed_key_class_then_lookup_of_pointed_entity_throws() {
      final var e =
          assertThrows(
              MissingKeysException.class, () -> EntityMetadataInventory.getForClass(Shelf.class));
      assertInstanceOf(MissingOrderException.class, e.getCause());
    }

    private static class Warehouse {

      String locationCode;
      int localId;
      int capacity;
    }

    @KeyClass(Warehouse.class)
    private record WarehouseKey(@KeyOrder(2) int localId, @KeyOrder(1) String locationCode) {

      static final String NOT_A_KEY_FIELD = "ignored";
    }

    private static class Shelf {

      String aisle;
    }

    // Malformed because KeyOrder is missing
    @KeyClass(Shelf.class)
    private record ShelfKey(String aisle) {}
  }

  /*
   * The inventory is global and cannot be reset, so every test registers its own entity class.
   * Conflicts are logged at ERROR level; that log output is expected here.
   */
  @Nested
  class Adding_descriptors {

    @Test
    void given_single_descriptor_then_returns_it() {
      final EntityDescriptor descriptor = descriptor(Single.class, "id");

      EntityMetadataInventory.add(descriptor);

      assertSame(descriptor, EntityMetadataInventory.getForClass(Single.class));
    }

    @Test
    void given_two_descriptors_for_same_entity_then_lookup_throws_conflicting_metadata_exception() {
      EntityMetadataInventory.add(descriptor(Doubled.class, "id"));
      EntityMetadataInventory.add(descriptor(Doubled.class, "other"));

      final ConflictingMetadataException e =
          assertThrows(
              ConflictingMetadataException.class,
              () -> EntityMetadataInventory.getForClass(Doubled.class));

      assertTrue(e.getMessage().contains(Doubled.class.getName()), e.getMessage());
      assertTrue(e.getMessage().contains("(id vs other)"), e.getMessage());
    }

    @Test
    void given_three_descriptors_for_same_entity_then_lookup_still_throws_and_reports_third() {
      EntityMetadataInventory.add(descriptor(Tripled.class, "id"));
      EntityMetadataInventory.add(descriptor(Tripled.class, "other"));
      EntityMetadataInventory.add(descriptor(Tripled.class, "third"));

      final ConflictingMetadataException e =
          assertThrows(
              ConflictingMetadataException.class,
              () -> EntityMetadataInventory.getForClass(Tripled.class));

      assertEquals(1, e.getSuppressed().length);
      assertTrue(
          e.getSuppressed()[0].getMessage().contains("third"), e.getSuppressed()[0].getMessage());
    }

    private EntityDescriptor descriptor(final Class<?> entityClass, final String fieldName) {
      final Field field;
      try {
        field = entityClass.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        throw new AssertionError("Test entity %s lacks field %s".formatted(entityClass, fieldName));
      }
      return new EntityDescriptor(
          entityClass,
          new PrimaryKeyDescriptor(List.of(new AttributeDescriptor(field, ObjectToString.class))),
          entityClass.getName());
    }

    private static class Single {

      String id;
    }

    private static class Doubled {

      String id;
      String other;
    }

    private static class Tripled {

      String id;
      String other;
      String third;
    }
  }

  @Nested
  class Recording_failures {

    @Test
    void given_recorded_failure_then_lookup_throws_missing_keys() {
      final var failure = new IllegalArgumentException("some kind of failure");
      EntityMetadataInventory.addFailure(Car.class.getName(), failure);

      final var e =
          assertThrows(
              MissingKeysException.class, () -> EntityMetadataInventory.getForClass(Car.class));

      assertSame(failure, e.getCause());
    }

    @Test
    void given_recorded_linkage_error_then_lookup_throws_missing_keys() {
      final var failure = new NoClassDefFoundError("com/example/Gone");
      EntityMetadataInventory.addFailure(Car.class.getName(), failure);

      final var e =
          assertThrows(
              MissingKeysException.class, () -> EntityMetadataInventory.getForClass(Car.class));

      assertSame(failure, e.getCause());
    }

    private static class Car {

      String owner;
    }
  }
}
