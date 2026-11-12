/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion;
import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.mappers.IntegerZeroPadder;
import hu.bme.mit.ftsrg.hypernate.metadata.EntityMetadataInventory;
import hu.bme.mit.ftsrg.hypernate.metadata.EntityMetadataProvider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.hyperledger.fabric.shim.ChaincodeStub;

@Loggable(Loggable.DEBUG)
public class SchemaRegistry {

  private static final String SCHEMA_NAMESPACE = "_hypernate_schema";

  private record LocalSchemaEntry(int version, byte[] schemaBytes) {}

  private final Map<String, LocalSchemaEntry> localCache = new HashMap<>();

  public SchemaRegistry(HypernateSchemaGenerator generator) {
    Set<Class<?>> entities = EntityMetadataInventory.getEntityList();
    for (Class<?> clazz : entities) {
      EntityType entityType = clazz.getAnnotation(EntityType.class);
      if (entityType != null) {
        if (SCHEMA_NAMESPACE.equals(entityType.value())) {
          throw new IllegalArgumentException(
              "EntityType '"
                  + SCHEMA_NAMESPACE
                  + "' is reserved for internal Hypernate schema storage.");
        }
      }
      String entityTypeName = new EntityMetadataProvider().typeStringFor(clazz);
      if (localCache.containsKey(entityTypeName)) {
        throw new IllegalArgumentException(
            "Type collision: two different classes use the same entity type name: "
                + entityTypeName);
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
        byte[] classSchema = generator.generateSchema(clazz);
        localCache.put(entityTypeName, new LocalSchemaEntry(1, classSchema));
      }
    }
  }

  public void registerSchema(HypernateContext ctx, String entityType) {
    LocalSchemaEntry localSchemaEntry = localCache.get(entityType);
    if (localSchemaEntry == null) {
      throw new SchemaVersionNotFoundException(entityType);
    }

    String localSchemaVersion = new IntegerZeroPadder().apply(localSchemaEntry.version());
    String schemaKey = SCHEMA_NAMESPACE + entityType + localSchemaVersion;

    if (ctx.getLedgerVerifiedCache().containsKey(schemaKey)) return;

    ChaincodeStub stub = ctx.getStub();
    String compositeKey =
        stub.createCompositeKey(SCHEMA_NAMESPACE, entityType, localSchemaVersion).toString();
    byte[] schemaBytes = stub.getState(compositeKey);

    if (schemaBytes != null && schemaBytes.length > 0) {
      if (!Arrays.equals(localCache.get(entityType).schemaBytes(), schemaBytes)) {
        throw new SchemaMismatchException(entityType);
      }
      ctx.getLedgerVerifiedCache().put(schemaKey, schemaBytes);
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
    ctx.getLedgerVerifiedCache().put(schemaKey, localSchemaEntry.schemaBytes());
  }

  public byte[] getSchema(HypernateContext ctx, String entityType, int version) {
    String schemaVersion = new IntegerZeroPadder().apply(version);
    String schemaKey = SCHEMA_NAMESPACE + entityType + schemaVersion;

    if (ctx.getLedgerVerifiedCache().containsKey(schemaKey)) {
      return ctx.getLedgerVerifiedCache().get(schemaKey);
    }

    ChaincodeStub stub = ctx.getStub();
    String compositeKey =
        stub.createCompositeKey(SCHEMA_NAMESPACE, entityType, schemaVersion).toString();
    byte[] schemaBytes = stub.getState(compositeKey);
    if (schemaBytes == null || schemaBytes.length == 0) {
      throw new SchemaNotFoundException(entityType);
    }
    ctx.getLedgerVerifiedCache().put(schemaKey, schemaBytes);
    return schemaBytes;
  }
}
