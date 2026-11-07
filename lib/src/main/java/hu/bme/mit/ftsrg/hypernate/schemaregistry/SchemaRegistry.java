/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import com.jcabi.aspects.Loggable;

import hu.bme.mit.ftsrg.hypernate.annotations.EntityType;
import hu.bme.mit.ftsrg.hypernate.metadata.EntityMetadataInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Loggable(Loggable.DEBUG)
public class SchemaRegistry {

  private static final String SCHEMA_NAMESPACE = "_hypernate_schema";
  private record LocalSchemaEntry(int version, byte[] schemaBytes) {}

  private final Map<String, LocalSchemaEntry> localCache = new HashMap<>();

  private final Map<String, byte[]> ledgerVerifiedCache = new ConcurrentHashMap<>();

  public SchemaRegistry(HypernateSchemaGenerator generator) {
    Set<Class<?>> entities = EntityMetadataInventory.getEntityList();
    for(Class<?> clazz : entities){
      EntityType type = clazz.getAnnotation(EntityType.class);
      if(type != null){
        if(SCHEMA_NAMESPACE.equals(type.value())){
          throw new IllegalArgumentException(
                    "EntityType '" + SCHEMA_NAMESPACE + "' is reserved for internal Hypernate schema storage."
          );
        }
        byte[] classSchema = generator.generateSchema(clazz);
        localCache.put(clazz.getName(), new LocalSchemaEntry(type.version(), classSchema));
      }else{
        throw new EntityTypeNotFoundException(clazz.getName());
      }
    }
  }
}
