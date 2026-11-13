/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion;
import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.schemaregistry.SchemaRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class HypernateEntitySerializerTest {

  private static final TestEntity entity = new TestEntity("fooValue", 110);

  @Mock private SchemaRegistry schemaRegistry;

  @Mock private HypernateContext ctx;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private HypernateEntitySerializer serializer;

  @EntityType("Person")
  @SchemaVersion(2)
  private record TestEntity(String foo, int bar) {}

  private record DefaultEntity(String value) {}

  private record EntityWithReservedField(String hypernate, String name) {}

  @BeforeEach
  void setUp() {
    serializer = new HypernateEntitySerializer(schemaRegistry, ctx);
  }

  @Nested
  class RoundTripping {

    @Test
    void given_annotated_entity_then_serializes_and_deserializes_to_equal_instance() {

      final byte[] buffer = serializer.toBuffer(entity);
      final TestEntity result = serializer.fromBuffer(buffer, TestEntity.class);

      assertEquals(entity, result);
    }

    @Test
    void given_unannotated_entity_then_serializes_and_deserializes_to_equal_instance() {
      final DefaultEntity defaultEntity = new DefaultEntity("testValue");

      final byte[] buffer = serializer.toBuffer(defaultEntity);
      final DefaultEntity result = serializer.fromBuffer(buffer, DefaultEntity.class);

      assertEquals(defaultEntity, result);
    }
  }

  @Nested
  class EmittedMetadataLayout {

    @Test
    void given_annotated_entity_when_serialized_then_injects_flattened_metadata()
        throws IOException {

      final byte[] buffer = serializer.toBuffer(entity);
      final JsonNode root = objectMapper.readTree(buffer);

      assertTrue(root.isObject());
      assertEquals("fooValue", root.get("foo").asText());
      assertEquals(110, root.get("bar").asInt());

      assertTrue(root.has("hypernate"));
      final JsonNode metadata = root.get("hypernate");
      assertTrue(metadata.isObject());
      assertEquals("Person", metadata.get("type").asText());
      assertTrue(metadata.get("version").isInt());
      assertEquals(2, metadata.get("version").asInt());

      then(schemaRegistry).should().registerSchema(ctx, "Person");
    }

    @Test
    void given_unannotated_entity_when_serialized_then_uses_class_name_and_version_one()
        throws IOException {
      final DefaultEntity entity = new DefaultEntity("hello");

      final byte[] buffer = serializer.toBuffer(entity);
      final JsonNode root = objectMapper.readTree(buffer);

      assertEquals("hello", root.get("value").asText());
      final JsonNode metadata = root.get("hypernate");
      assertEquals(DefaultEntity.class.getName(), metadata.get("type").asText());
      assertEquals(1, metadata.get("version").asInt());

      then(schemaRegistry).should().registerSchema(ctx, DefaultEntity.class.getName());
    }
  }

  @Nested
  class ReservedHypernatePropertyRejection {

    @Test
    void given_entity_with_reserved_hypernate_field_then_throws_serialization_exception() {
      final EntityWithReservedField entity = new EntityWithReservedField("illegal", "John");

      final SerializationException exception =
          assertThrows(SerializationException.class, () -> serializer.toBuffer(entity));

      assertTrue(exception.getMessage().contains("reserved name 'hypernate'"));
    }
  }

  @Nested
  class RecordWithoutMetadata {

    @Test
    void given_json_without_hypernate_metadata_when_deserialized_then_throws_serialization_exception() {
      final byte[] buffer =
          "{\"foo\":\"fooValue\",\"bar\":110}".getBytes(StandardCharsets.UTF_8);

      final SerializationException exception =
          assertThrows(
              SerializationException.class, () -> serializer.fromBuffer(buffer, TestEntity.class));

      assertTrue(
          exception.getMessage().contains("missing the required 'hypernate' metadata object"));
    }

    @Test
    void given_non_object_json_when_deserialized_then_throws_serialization_exception() {
      final byte[] buffer = "[1, 2, 3]".getBytes(StandardCharsets.UTF_8);

      final SerializationException exception =
          assertThrows(
              SerializationException.class, () -> serializer.fromBuffer(buffer, TestEntity.class));

      assertTrue(exception.getMessage().contains("is not a JSON object"));
    }

    @Test
    void given_mismatched_version_when_deserialized_then_throws_serialization_exception() {
      final byte[] buffer =
          "{\"foo\":\"fooValue\",\"bar\":110,\"hypernate\":{\"type\":\"Person\",\"version\":1}}"
              .getBytes(StandardCharsets.UTF_8);

      final SerializationException exception =
          assertThrows(
              SerializationException.class, () -> serializer.fromBuffer(buffer, TestEntity.class));

      assertTrue(exception.getMessage().contains("Mismatch in the data version"));
    }

    @Test
    void given_mismatched_type_when_deserialized_then_throws_serialization_exception() {
      final byte[] buffer =
          "{\"foo\":\"fooValue\",\"bar\":110,\"hypernate\":{\"type\":\"OtherType\",\"version\":2}}"
              .getBytes(StandardCharsets.UTF_8);

      final SerializationException exception =
          assertThrows(
              SerializationException.class, () -> serializer.fromBuffer(buffer, TestEntity.class));

      assertTrue(exception.getMessage().contains("Mismatch in the data version"));
    }
  }

  @Nested
  class Validation {

    @Test
    void given_null_entity_when_serialized_then_throws_serialization_exception() {
      final SerializationException exception =
          assertThrows(SerializationException.class, () -> serializer.toBuffer(null));

      assertTrue(exception.getMessage().contains("Cannot serialize null entity"));
    }
  }
}
