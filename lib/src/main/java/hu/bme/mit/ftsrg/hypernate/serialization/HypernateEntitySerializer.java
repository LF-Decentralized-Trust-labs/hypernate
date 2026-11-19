/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion;
import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.schemaregistry.HypernateDefaultValues;
import hu.bme.mit.ftsrg.hypernate.schemaregistry.HypernateSchemaDiffer.FieldChange;
import hu.bme.mit.ftsrg.hypernate.schemaregistry.SchemaRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HypernateEntitySerializer implements EntitySerializer {

  private static final String HYPERNATE_FIELD = "hypernate";
  private static final String VERSION_FIELD = "version";
  private static final String TYPE_FIELD = "type";

  private final ObjectMapper objectMapper;
  private final SchemaRegistry schemaRegistry;
  private final HypernateContext ctx;

  public HypernateEntitySerializer(
      final SchemaRegistry schemaRegistry, final HypernateContext ctx) {
    this.objectMapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    this.schemaRegistry = schemaRegistry;
    this.ctx = ctx;
  }

  @Override
  public <T> byte[] toBuffer(final T entity) {
    if (entity == null) {
      throw new SerializationException("Cannot serialize null entity");
    }

    final Class<?> clazz = entity.getClass();
    schemaRegistry.registerSchema(ctx, resolveEntityTypeName(clazz));

    try {
      final ObjectNode root = objectMapper.valueToTree(entity);
      if (root.has(HYPERNATE_FIELD)) {
        throw new SerializationException(
            "Entity "
                + clazz.getName()
                + " serializes a property to the reserved name '"
                + HYPERNATE_FIELD
                + "'");
      }
      final ObjectNode meta = root.putObject(HYPERNATE_FIELD);
      meta.put(TYPE_FIELD, resolveEntityTypeName(clazz));
      meta.put(VERSION_FIELD, resolveSchemaVersion(clazz));
      return objectMapper.writeValueAsBytes(root);
    } catch (SerializationException e) {
      throw e;
    } catch (IOException e) {
      throw new SerializationException("Failed to serialize entity " + clazz.getName(), e);
    }
  }

  @Override
  public <T> T fromBuffer(final byte[] buffer, final Class<T> clazz) {
    try {
      final JsonNode tree = objectMapper.readTree(buffer);
      if (!(tree instanceof ObjectNode root)) {
        throw new SerializationException(
            "Stored record for " + clazz.getName() + " is not a JSON object");
      }

      if (!root.has(HYPERNATE_FIELD)) {
        throw new SerializationException(
            "Stored record is missing the required '"
                + HYPERNATE_FIELD
                + "' metadata object; records without embedded Hypernate metadata are not"
                + " supported");
      }
      int entityVersion = root.get(HYPERNATE_FIELD).get(VERSION_FIELD).asInt();
      String entityType = root.get(HYPERNATE_FIELD).get(TYPE_FIELD).asText();
      if (!entityType.equals(resolveEntityTypeName(clazz))) {
        throw new SerializationException("Mismatch in the entityType");
      }
      int resolvedVersion = resolveSchemaVersion(clazz);
      if (entityVersion > resolvedVersion || entityVersion < 1) {
        throw new SerializationException("Mismatch in the data version");
      }
      root.remove(HYPERNATE_FIELD);
      if (entityVersion < resolvedVersion) {
        List<Map<FieldChange, JsonNode>> upcastMethods =
            schemaRegistry.upcastMethods(ctx, entityType, entityVersion, resolvedVersion);
        applyUpcasterMethods(root, upcastMethods);
      }

      return objectMapper.treeToValue(root, clazz);
    } catch (SerializationException e) {
      throw e;
    } catch (IOException e) {
      throw new SerializationException(
          "Failed to deserialize entity of type " + clazz.getName(), e);
    }
  }

  private static String resolveEntityTypeName(final Class<?> clazz) {
    final EntityType annotation = clazz.getAnnotation(EntityType.class);
    return (annotation != null) ? annotation.value() : clazz.getName();
  }

  private static int resolveSchemaVersion(final Class<?> clazz) throws SerializationException {
    final SchemaVersion annotation = clazz.getAnnotation(SchemaVersion.class);
    if (annotation == null) {
      return 1;
    }
    return annotation.value();
  }

  private ObjectNode applyUpcasterMethods(
      ObjectNode root, List<Map<FieldChange, JsonNode>> upcastMethods) {
    for (Map<FieldChange, JsonNode> methods : upcastMethods) {
      for (Map.Entry<FieldChange, JsonNode> entry : methods.entrySet()) {
        switch (entry.getKey().type()) {
          case ADD:
            JsonNode field = entry.getValue();
            String fieldName = entry.getKey().fieldName();
            String schemaType = field.has("type") ? field.get("type").asText() : null;
            String schemaFormat = field.has("format") ? field.get("format").asText() : null;
            addDefaultValue(root, fieldName, schemaType, schemaFormat);
            break;
          case DELETE:
            root.remove(entry.getKey().fieldName());
            break;
          case TYPE_CHANGE:
            JsonNode changedField = entry.getValue();
            String changedFieldName = entry.getKey().fieldName();
            String newType = changedField.get("type").asText();
            if (newType.equalsIgnoreCase("string")) {
              JsonNode oldValue = root.get(changedFieldName);
              if (oldValue != null && !oldValue.isNull()) {
                root.put(changedFieldName, oldValue.asText());
              }
            }
            break;
          case RENAME:
            String oldFieldName = entry.getValue().asText();
            String newFiealdName = entry.getKey().fieldName();
            if (root.has(oldFieldName)) {
              JsonNode value = root.remove(oldFieldName);
              root.set(newFiealdName, value);
            }
            break;
        }
      }
    }
    return root;
  }

  private void addDefaultValue(ObjectNode root, String fieldName, String type, String format) {
    if (type == null) {
      root.putNull(fieldName);
      return;
    }
    switch (type) {
      case "integer":
        if ("int64".equals(format)) {
          root.put(fieldName, HypernateDefaultValues.DEFAULT_LONG);
        } else {
          root.put(fieldName, HypernateDefaultValues.DEFAULT_INT);
        }
        break;
      case "number":
        root.put(fieldName, HypernateDefaultValues.DEFAULT_DOUBLE);
        break;
      case "string":
        root.put(fieldName, HypernateDefaultValues.DEFAULT_STRING);
        break;
      case "boolean":
        root.put(fieldName, HypernateDefaultValues.DEFAULT_BOOLEAN);
        break;
      default:
        root.putNull(fieldName);
        break;
    }
  }
}
