/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.serialization;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.schemaregistry.SchemaRegistry;
import java.io.IOException;
import org.hyperledger.fabric.shim.ChaincodeStub;

public class HypernateEntitySerializer implements EntitySerializer {

  private static final String HYPERNATE_FIELD = "hypernate";
  private static final String VERSION_FIELD = "version";
  private static final String TYPE_FIELD = "type";

  private final ObjectMapper objectMapper;
  private final SchemaRegistry schemaRegistry;
  private final ChaincodeStub stub;

  public HypernateEntitySerializer(final SchemaRegistry schemaRegistry, final ChaincodeStub stub) {
    this.objectMapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    this.schemaRegistry = schemaRegistry;
    this.stub = stub;
  }

  @Override
  public <T> byte[] toBuffer(final T entity) {
    if (entity == null) {
      throw new SerializationException("Cannot serialize null entity");
    }

    final Class<?> clazz = entity.getClass();
    schemaRegistry.registerSchema(stub, resolveEntityTypeName(clazz));

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
      final ObjectNode root = (ObjectNode) objectMapper.readTree(buffer);

      if (!root.has(HYPERNATE_FIELD)) {
        throw new SerializationException(
            "Stored record is missing the required '"
                + HYPERNATE_FIELD
                + "' metadata object; records without embedded Hypernate metadata are not"
                + " supported");
      }

      root.remove(HYPERNATE_FIELD);
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
    return annotation.value();
  }
}
