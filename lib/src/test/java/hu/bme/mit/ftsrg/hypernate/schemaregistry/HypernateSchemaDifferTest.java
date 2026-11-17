/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import hu.bme.mit.ftsrg.hypernate.schemaregistry.HypernateSchemaDiffer.FieldChange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HypernateSchemaDifferTest {

  private static byte[] createSchema(String propertiesJson) {
    return ("{\"type\":\"object\",\"properties\":{" + propertiesJson + "}}")
        .getBytes(StandardCharsets.UTF_8);
  }

  @Nested
  class IdenticalSchemas {

    @Test
    void given_identical_schemas_then_no_changes_detected() throws IOException {
      byte[] schema =
          createSchema(
              "\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\",\"format\":\"int32\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(schema, schema);

      assertTrue(differ.getCahnges().isEmpty());
    }

    @Test
    void given_same_properties_different_order_then_no_changes_detected() throws IOException {
      byte[] oldSchema =
          createSchema(
              "\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\",\"format\":\"int32\"}");
      byte[] newSchema =
          createSchema(
              "\"age\":{\"type\":\"integer\",\"format\":\"int32\"},\"name\":{\"type\":\"string\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      assertTrue(differ.getCahnges().isEmpty());
    }
  }

  @Nested
  class FieldAdditions {

    @Test
    void given_new_field_in_new_schema_then_detected_as_add() throws IOException {
      byte[] oldSchema = createSchema("\"name\":{\"type\":\"string\"}");
      byte[] newSchema =
          createSchema(
              "\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\",\"format\":\"email\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      assertEquals(1, changes.size());

      FieldChange expectedChange = new FieldChange("email", HypernateShemaChangeType.ADD);
      assertTrue(changes.containsKey(expectedChange));

      JsonNode addedFieldNode = changes.get(expectedChange);
      assertNotNull(addedFieldNode);
      assertEquals("string", addedFieldNode.get("type").asText());
      assertEquals("email", addedFieldNode.get("format").asText());
    }

    @Test
    void given_multiple_new_fields_then_all_detected_as_add() throws IOException {
      byte[] oldSchema = createSchema("\"id\":{\"type\":\"string\"}");
      byte[] newSchema =
          createSchema(
              "\"id\":{\"type\":\"string\"},"
                  + "\"firstName\":{\"type\":\"string\"},"
                  + "\"lastName\":{\"type\":\"string\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      assertEquals(2, changes.size());
      assertTrue(changes.containsKey(new FieldChange("firstName", HypernateShemaChangeType.ADD)));
      assertTrue(changes.containsKey(new FieldChange("lastName", HypernateShemaChangeType.ADD)));
    }
  }

  @Nested
  class FieldDeletions {

    @Test
    void given_field_removed_in_new_schema_then_detected_as_delete() throws IOException {
      byte[] oldSchema =
          createSchema(
              "\"name\":{\"type\":\"string\"},\"deprecated\":{\"type\":\"string\"}");
      byte[] newSchema = createSchema("\"name\":{\"type\":\"string\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      assertEquals(1, changes.size());

      FieldChange expectedChange = new FieldChange("deprecated", HypernateShemaChangeType.DELETE);
      assertTrue(changes.containsKey(expectedChange));

      JsonNode deletedFieldNode = changes.get(expectedChange);
      assertNotNull(deletedFieldNode);
      assertEquals("string", deletedFieldNode.get("type").asText());
    }

    @Test
    void given_multiple_fields_removed_then_all_detected_as_delete() throws IOException {
      byte[] oldSchema =
          createSchema(
              "\"keep\":{\"type\":\"string\"},"
                  + "\"remove1\":{\"type\":\"string\"},"
                  + "\"remove2\":{\"type\":\"integer\"}");
      byte[] newSchema = createSchema("\"keep\":{\"type\":\"string\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      assertEquals(2, changes.size());
      assertTrue(
          changes.containsKey(new FieldChange("remove1", HypernateShemaChangeType.DELETE)));
      assertTrue(
          changes.containsKey(new FieldChange("remove2", HypernateShemaChangeType.DELETE)));
    }
  }

  @Nested
  class AllowedTypeChanges {

    @Test
    void given_int32_widened_to_int64_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"score\":{\"type\":\"integer\",\"format\":\"int32\"}");
      byte[] newSchema = createSchema("\"score\":{\"type\":\"integer\",\"format\":\"int64\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      assertEquals(1, changes.size());

      FieldChange expected = new FieldChange("score", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
      assertEquals("int64", changes.get(expected).get("format").asText());
    }

    @Test
    void given_int32_widened_to_float_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"score\":{\"type\":\"integer\",\"format\":\"int32\"}");
      byte[] newSchema = createSchema("\"score\":{\"type\":\"number\",\"format\":\"float\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("score", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
      assertEquals("number", changes.get(expected).get("type").asText());
      assertEquals("float", changes.get(expected).get("format").asText());
    }

    @Test
    void given_int32_widened_to_double_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"score\":{\"type\":\"integer\",\"format\":\"int32\"}");
      byte[] newSchema = createSchema("\"score\":{\"type\":\"number\",\"format\":\"double\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("score", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
      assertEquals("number", changes.get(expected).get("type").asText());
      assertEquals("double", changes.get(expected).get("format").asText());
    }

    @Test
    void given_int32_widened_to_number_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"score\":{\"type\":\"integer\",\"format\":\"int32\"}");
      byte[] newSchema = createSchema("\"score\":{\"type\":\"number\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("score", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
      assertEquals("number", changes.get(expected).get("type").asText());
    }

    @Test
    void given_int64_widened_to_double_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"score\":{\"type\":\"integer\",\"format\":\"int64\"}");
      byte[] newSchema = createSchema("\"score\":{\"type\":\"number\",\"format\":\"double\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("score", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
    }

    @Test
    void given_int64_widened_to_number_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"score\":{\"type\":\"integer\",\"format\":\"int64\"}");
      byte[] newSchema = createSchema("\"score\":{\"type\":\"number\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("score", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
    }

    @Test
    void given_float_widened_to_double_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"ratio\":{\"type\":\"number\",\"format\":\"float\"}");
      byte[] newSchema = createSchema("\"ratio\":{\"type\":\"number\",\"format\":\"double\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("ratio", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
    }

    @Test
    void given_float_widened_to_number_then_detected_as_type_change() throws IOException {
      byte[] oldSchema = createSchema("\"ratio\":{\"type\":\"number\",\"format\":\"float\"}");
      byte[] newSchema = createSchema("\"ratio\":{\"type\":\"number\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("ratio", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
    }

    @Test
    void given_unformatted_integer_widened_to_number_then_detected_as_type_change()
        throws IOException {
      byte[] oldSchema = createSchema("\"count\":{\"type\":\"integer\"}");
      byte[] newSchema = createSchema("\"count\":{\"type\":\"number\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("count", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
    }

    @Test
    void given_same_type_with_different_attributes_then_detected_as_type_change()
        throws IOException {
      byte[] oldSchema =
          createSchema("\"name\":{\"type\":\"string\",\"description\":\"old description\"}");
      byte[] newSchema =
          createSchema("\"name\":{\"type\":\"string\",\"description\":\"new description\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      FieldChange expected = new FieldChange("name", HypernateShemaChangeType.TYPE_CHANGE);
      assertTrue(changes.containsKey(expected));
    }
  }

  @Nested
  class InvalidTypeChanges {

    @Test
    void given_int64_narrowed_to_int32_then_throws_invalid_type_change_exception() {
      byte[] oldSchema = createSchema("\"id\":{\"type\":\"integer\",\"format\":\"int64\"}");
      byte[] newSchema = createSchema("\"id\":{\"type\":\"integer\",\"format\":\"int32\"}");

      InvalidTypeChangeException exception =
          assertThrows(
              InvalidTypeChangeException.class,
              () -> new HypernateSchemaDiffer(oldSchema, newSchema));

      assertTrue(exception.getMessage().contains("integer|int64"));
      assertTrue(exception.getMessage().contains("integer|int32"));
    }

    @Test
    void given_string_to_integer_then_throws_invalid_type_change_exception() {
      byte[] oldSchema = createSchema("\"data\":{\"type\":\"string\"}");
      byte[] newSchema = createSchema("\"data\":{\"type\":\"integer\"}");

      InvalidTypeChangeException exception =
          assertThrows(
              InvalidTypeChangeException.class,
              () -> new HypernateSchemaDiffer(oldSchema, newSchema));

      assertTrue(exception.getMessage().contains("string"));
      assertTrue(exception.getMessage().contains("integer"));
    }

    @Test
    void given_integer_to_string_then_throws_invalid_type_change_exception() {
      byte[] oldSchema = createSchema("\"data\":{\"type\":\"integer\"}");
      byte[] newSchema = createSchema("\"data\":{\"type\":\"string\"}");

      InvalidTypeChangeException exception =
          assertThrows(
              InvalidTypeChangeException.class,
              () -> new HypernateSchemaDiffer(oldSchema, newSchema));

      assertTrue(exception.getMessage().contains("integer"));
      assertTrue(exception.getMessage().contains("string"));
    }

    @Test
    void given_double_narrowed_to_float_then_throws_invalid_type_change_exception() {
      byte[] oldSchema = createSchema("\"val\":{\"type\":\"number\",\"format\":\"double\"}");
      byte[] newSchema = createSchema("\"val\":{\"type\":\"number\",\"format\":\"float\"}");

      assertThrows(
          InvalidTypeChangeException.class,
          () -> new HypernateSchemaDiffer(oldSchema, newSchema));
    }

    @Test
    void given_boolean_to_integer_then_throws_invalid_type_change_exception() {
      byte[] oldSchema = createSchema("\"flag\":{\"type\":\"boolean\"}");
      byte[] newSchema = createSchema("\"flag\":{\"type\":\"integer\"}");

      assertThrows(
          InvalidTypeChangeException.class,
          () -> new HypernateSchemaDiffer(oldSchema, newSchema));
    }
  }

  @Nested
  class CombinedEvolution {

    @Test
    void given_schema_with_adds_deletes_and_type_changes_then_all_detected_correctly()
        throws IOException {
      byte[] oldSchema =
          createSchema(
              "\"unchanged\":{\"type\":\"string\"},"
                  + "\"removed\":{\"type\":\"string\"},"
                  + "\"widened\":{\"type\":\"integer\",\"format\":\"int32\"}");
      byte[] newSchema =
          createSchema(
              "\"unchanged\":{\"type\":\"string\"},"
                  + "\"added\":{\"type\":\"boolean\"},"
                  + "\"widened\":{\"type\":\"integer\",\"format\":\"int64\"}");

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(oldSchema, newSchema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      assertEquals(3, changes.size());

      assertTrue(changes.containsKey(new FieldChange("added", HypernateShemaChangeType.ADD)));
      assertTrue(changes.containsKey(new FieldChange("removed", HypernateShemaChangeType.DELETE)));
      assertTrue(
          changes.containsKey(new FieldChange("widened", HypernateShemaChangeType.TYPE_CHANGE)));
    }
  }

  @Nested
  class EndToEndWithSchemaGenerator {

    private record V1Entity(String name, int count, String obsolete) {}

    private record V2Entity(String name, long count, String replacement) {}

    @Test
    void given_v1_and_v2_entity_classes_when_diffed_then_detects_all_differences()
        throws IOException {
      HypernateSchemaGenerator generator = new HypernateSchemaGenerator();
      byte[] v1Schema = generator.generateSchema(V1Entity.class);
      byte[] v2Schema = generator.generateSchema(V2Entity.class);

      HypernateSchemaDiffer differ = new HypernateSchemaDiffer(v1Schema, v2Schema);

      Map<FieldChange, JsonNode> changes = differ.getCahnges();
      assertEquals(3, changes.size());

      assertTrue(
          changes.containsKey(new FieldChange("obsolete", HypernateShemaChangeType.DELETE)));
      assertTrue(
          changes.containsKey(new FieldChange("replacement", HypernateShemaChangeType.ADD)));
      assertTrue(
          changes.containsKey(new FieldChange("count", HypernateShemaChangeType.TYPE_CHANGE)));
    }
  }
}
