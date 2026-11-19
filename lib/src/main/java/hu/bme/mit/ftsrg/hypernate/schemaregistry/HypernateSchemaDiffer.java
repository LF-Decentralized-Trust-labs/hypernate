/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

public class HypernateSchemaDiffer {

  ObjectMapper mapper;

  public record FieldChange(String fieldName, HypernateShemaChangeType type) {}

  @Getter private final Map<FieldChange, JsonNode> cahnges = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> allowedTypeChanges =
      Map.of(
          "integer|int32",
              Set.of("integer|int64", "number|float", "number|double", "number", "string"),
          "integer|int64", Set.of("number|double", "number", "string"),
          "number|float", Set.of("number|double", "number", "string"),
          "integer", Set.of("number", "string"),
          "number", Set.of("string"),
          "boolean", Set.of("string"));

  public HypernateSchemaDiffer(byte[] oldSchema, byte[] newSchema) throws IOException {
    this.mapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    JsonNode oldNode = mapper.readTree(oldSchema);
    JsonNode newNode = mapper.readTree(newSchema);

    if (oldNode.isObject() && newNode.isObject()) {
      calculateDifference((ObjectNode) oldNode, (ObjectNode) newNode);
    }
  }

  private void calculateDifference(ObjectNode oldNode, ObjectNode newNode) {
    ObjectNode oldProperties = (ObjectNode) oldNode.get("properties");
    ObjectNode newProperties = (ObjectNode) newNode.get("properties");

    Iterator<Entry<String, JsonNode>> oldFields = oldProperties.fields();
    while (oldFields.hasNext()) {
      Entry<String, JsonNode> oldFieldEntry = oldFields.next();
      JsonNode oldField = oldFieldEntry.getValue();
      if (!newProperties.has(oldFieldEntry.getKey())) {
        cahnges.put(
            new FieldChange(oldFieldEntry.getKey(), HypernateShemaChangeType.DELETE), oldField);
      } else {
        JsonNode newField = newProperties.get(oldFieldEntry.getKey());
        if (!oldField.equals(newField)) {
          String oldType = extractKey(oldField);
          String newType = extractKey(newField);
          checkTypeConversion(oldType, newType);
          cahnges.put(
              new FieldChange(oldFieldEntry.getKey(), HypernateShemaChangeType.TYPE_CHANGE),
              newField);
        }
      }
    }

    Iterator<Entry<String, JsonNode>> newFields = newProperties.fields();
    while (newFields.hasNext()) {
      Entry<String, JsonNode> newFieldEntry = newFields.next();
      JsonNode newField = newFieldEntry.getValue();
      if (!oldProperties.has(newFieldEntry.getKey())) {
        cahnges.put(
            new FieldChange(newFieldEntry.getKey(), HypernateShemaChangeType.ADD), newField);
      }
    }
  }

  private void checkTypeConversion(String fromType, String toType) {
    if (fromType.equals(toType)) {
      return;
    }
    Set<String> allowedTargets = allowedTypeChanges.get(fromType);

    if (allowedTargets == null || !allowedTargets.contains(toType)) {
      throw new InvalidTypeChangeException(fromType, toType);
    }
  }

  private String extractKey(JsonNode fieldNode) {
    if (fieldNode == null || !fieldNode.has("type")) {
      return "unknown";
    }

    String type = fieldNode.get("type").asText();
    if (fieldNode.has("format")) {
      return type + "|" + fieldNode.get("format").asText();
    }
    return type;
  }
}
