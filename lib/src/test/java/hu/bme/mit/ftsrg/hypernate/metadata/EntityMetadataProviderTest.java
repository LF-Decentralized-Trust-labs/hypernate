/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hu.bme.mit.ftsrg.hypernate.annotations.*;
import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.registry.MissingKeysException;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EntityMetadataProviderTest {

  private final EntityMetadataProvider provider = new EntityMetadataProvider();

  // This mapper is invalid because it does not have a no-arg constructor
  private static class InvalidMapper implements AttributeMapper {

    InvalidMapper(Void ctorParam) {}

    @Override
    public String apply(Object o) {
      return null;
    }
  }

  @Nested
  class Building_keys {

    @Test
    void given_mapper_info_on_key_class_field_then_maps_key_part() {
      final var w = new Warehouse("WH-0001", "Europe");

      final String key = provider.keyProviderFor(Warehouse.class).getKey(w);

      assertEquals(
          new CompositeKey(Warehouse.class.getName(), w.id, PrefixMapper.PREFIX + w.location)
              .toString(),
          key);
    }

    @Test
    void given_key_field_declared_on_superclass_then_reads_it_from_entity() {
      final var forklift = Forklift.builder().licensePlate("ABC-123").bay(4).build();

      final String key = provider.keyProviderFor(Forklift.class).getKey(forklift);

      assertEquals(
          new CompositeKey(
                  Forklift.class.getName(), forklift.licensePlate, Integer.toString(forklift.bay))
              .toString(),
          key);
    }

    static class PrefixMapper implements AttributeMapper {

      static final String PREFIX = "TESTPREFIX";

      @Override
      public String apply(Object o) {
        return PREFIX + o.toString();
      }
    }

    private record Warehouse(String id, String location) {}

    @KeyClass(Warehouse.class)
    private record WarehouseKey(
        @KeyOrder(1) String id, @KeyOrder(2) @MapperInfo(PrefixMapper.class) String location) {}

    @SuperBuilder
    @FieldNameConstants
    private static class Vehicle {

      final String licensePlate;
    }

    @SuperBuilder
    @PrimaryKey({
      @AttributeInfo(name = Vehicle.Fields.licensePlate),
      @AttributeInfo(name = Forklift.Fields.bay)
    })
    @FieldNameConstants
    private static class Forklift extends Vehicle {

      final int bay;
    }
  }

  @Nested
  class Instantiating_mappers {

    @Test
    void given_maper_without_no_arg_constructor_then_throws_missing_keys() {
      assertThrows(MissingKeysException.class, () -> provider.keyProviderFor(Parcel.class));
    }

    @PrimaryKey(@AttributeInfo(name = Parcel.Fields.code, mapper = InvalidMapper.class))
    @FieldNameConstants
    private record Parcel(String code) {}
  }
}
