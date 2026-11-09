/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion;
import hu.bme.mit.ftsrg.hypernate.mappers.IntegerZeroPadder;
import hu.bme.mit.ftsrg.hypernate.metadata.EntityMetadataInventory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hyperledger.fabric.shim.ChaincodeStub;

@Loggable(Loggable.DEBUG)
public class SchemaRegistry {

  private static final String SCHEMA_NAMESPACE = "_hypernate_schema";

  private record LocalSchemaEntry(int version, byte[] schemaBytes) {}

  private final Map<String, LocalSchemaEntry> localCache = new HashMap<>();

  private final Map<String, byte[]> ledgerVerifiedCache = new ConcurrentHashMap<>();

  public SchemaRegistry(HypernateSchemaGenerator generator) {
    Set<Class<?>> entities = EntityMetadataInventory.getEntityList();
    for (Class<?> clazz : entities) {
      EntityType entityType = clazz.getAnnotation(EntityType.class);
      String entityTypeName;
      if (entityType != null) {
        if (SCHEMA_NAMESPACE.equals(entityType.value())) {
          throw new IllegalArgumentException(
              "EntityType '"
                  + SCHEMA_NAMESPACE
                  + "' is reserved for internal Hypernate schema storage.");
        }
        if (entityType.value().isBlank()) {
        throw new IllegalArgumentException(
          String.format(
              "The @EntityType annotation on class %s has an empty or blank value",
              clazz.getName()));
        }
        entityTypeName = entityType.value();
      } else {
        entityTypeName = clazz.getName();
      }
      if(localCache.containsKey(entityTypeName)){
        throw new IllegalArgumentException(
          "Type collision: two different classes use the same entity type name: " + entityType.value());
      }
      SchemaVersion schemaVersion = clazz.getAnnotation(SchemaVersion.class);
      if (schemaVersion != null) {
        if (schemaVersion.value() < 1) {
          throw new IllegalArgumentException(
              "Invalid version: " + schemaVersion.value() + "for class: " + entityTypeName);
        }
        byte[] classSchema = generator.generateSchema(clazz);
        localCache.put(entityTypeName, new LocalSchemaEntry(schemaVersion.value(), classSchema));
      } else {
        throw new SchemaVersionNotFoundException(entityTypeName);
      }
    }
  }

  public void registerSchema(ChaincodeStub stub, String entityType) {
    LocalSchemaEntry localSchemaEntry = localCache.get(entityType);
    // We don't have a ledger writeable class
    if (localSchemaEntry == null) {
      throw new SchemaVersionNotFoundException(entityType);
    }

    String localSchemaVersion = new IntegerZeroPadder().apply(localSchemaEntry.version());
    String schemaKey = SCHEMA_NAMESPACE + entityType + localSchemaVersion;

    if (ledgerVerifiedCache.containsKey(schemaKey)) return;

    String compositeKey =
        stub.createCompositeKey(SCHEMA_NAMESPACE, entityType, localSchemaVersion).toString();
    byte[] schemaBytes = stub.getState(compositeKey);

    if (schemaBytes != null && schemaBytes.length > 0) {
      if (!Arrays.equals(localCache.get(entityType).schemaBytes(), schemaBytes)) {
        throw new SchemaMismatchException(entityType);
      }
      ledgerVerifiedCache.put(schemaKey, schemaBytes);
      return;
    }

    if (localSchemaEntry.version() > 1) {
      String prevSchemaVersion = new IntegerZeroPadder().apply(localSchemaEntry.version() - 1);
      String prevcompositeKey =
          stub.createCompositeKey(SCHEMA_NAMESPACE, entityType, prevSchemaVersion).toString();
      byte[] prevschemaBytes = stub.getState(prevcompositeKey);
      if (prevschemaBytes == null || prevschemaBytes.length == 0) {
        throw new MissingPreviousSchemaVersionException(entityType, localSchemaEntry.version() - 1);
      }
    }

    stub.putState(compositeKey, localSchemaEntry.schemaBytes());
  }

  public byte[] getSchema(ChaincodeStub stub, String entityType, int version) {
    LocalSchemaEntry local = localCache.get(entityType);
    if (local != null && local.version() == version) {
      return local.schemaBytes();
    }
    String schemaVersion = new IntegerZeroPadder().apply(version);
    String schemaKey = SCHEMA_NAMESPACE + entityType + schemaVersion;
    return ledgerVerifiedCache.computeIfAbsent(
        schemaKey,
        k -> {
          String compositeKey =
              stub.createCompositeKey(SCHEMA_NAMESPACE, entityType, schemaVersion).toString();
          byte[] schemaBytes = stub.getState(compositeKey);
          if (schemaBytes == null || schemaBytes.length == 0) {
            throw new SchemaNotFoundException(entityType);
          }
          return schemaBytes;
        });
  }
}
