/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.serialization;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.schemaregistry.SchemaRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hyperledger.fabric.shim.ChaincodeStub;

public class HypernateEntitySerializer implements EntitySerializer {

  private static final String HYPERNATE_FIELD = "hypernate";
  private static final String VERSION_FIELD = "version";
  private static final String TYPE_FIELD = "type";

  private final ObjectMapper objectMapper;
  private final SchemaRegistry schemaRegistry;
  private final ChaincodeStub stub;

  private final Set<Class<?>> validatedClasses = ConcurrentHashMap.newKeySet();

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
    validateNoReservedProperty(clazz);
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
    } catch (IOException e) {
      throw new SerializationException("Failed to serialize entity " + clazz.getName(), e);
    }
  }

  @Override
  public <T> T fromBuffer(final byte[] buffer, final Class<T> clazz) {
    final String json = new String(buffer, StandardCharsets.UTF_8);

    try {
      final ObjectNode root = (ObjectNode) objectMapper.readTree(json);

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

  private <T> void validateNoReservedProperty(final Class<T> clazz) throws SerializationException {
    if (validatedClasses.contains(clazz)) {
      return;
    }
    final JavaType javaType = objectMapper.constructType(clazz);
    final BeanDescription beanDesc = objectMapper.getSerializationConfig().introspect(javaType);

    for (final BeanPropertyDefinition prop : beanDesc.findProperties()) {
      if (HYPERNATE_FIELD.equals(prop.getName())) {
        throw new SerializationException(
            "Entity "
                + clazz.getName()
                + " declares a field or getter that serializes to the reserved name '"
                + HYPERNATE_FIELD
                + "'");
      }
    }

    validatedClasses.add(clazz);
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
