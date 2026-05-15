package hu.bme.mit.ftsrg.hypernate.registry;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.annotations.EntityKeyProvider;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Loggable(Loggable.DEBUG)
public class EntityMetaDataProvider {
  private static final Logger logger = LoggerFactory.getLogger(EntityMetaDataProvider.class);
  private EntityMetaDataInventory metaInventory = new EntityMetaDataInventory();
  private Map<Class<?>, EntityKeyProvider> keyProviders = new HashMap<>();

  public EntityMetaDataProvider() {

  }

  <T> String getType(final T entity) {
    return getType(entity.getClass());
  }

  <T> String getType(final Class<T> clazz) {
    return clazz.getName().toUpperCase();
  }

  <T> int getPrimaryKeyCount(final Class<T> clazz) {
    return clazz.getAnnotation(PrimaryKey.class) != null
        ? clazz.getAnnotation(PrimaryKey.class).value().length
        : 0;
  }

  <T> String[] mapKeyPartsToString(final T entity, final Object... keyParts) {
    return mapKeyPartsToString(entity.getClass(), keyParts);
  }

  <T> String[] mapKeyPartsToString(final Class<T> clazz, final Object... keyParts) {
    EntityMeta em = metaInventory.getForClass(clazz);
    if (em == null) {
      throw new ChaincodeException("Could not find key generation method.");
    }
    List<Field> fields = new ArrayList<>();
    List<AttributeMapper> mappers = new ArrayList<>();
    PrimaryKeyDescriptor pk = em.getPrimaryKeyDescriptor();
    List<AttributeDescriptor> pkAttributeDescriptors = pk.getAttributeDescriptiors();
    for (AttributeDescriptor descriptor : pkAttributeDescriptors) {
      try {
        Field field = clazz.getDeclaredField(descriptor.getAttrFieldName());
        field.setAccessible(true);
        fields.add(field);
      } catch (Exception e) {
        throw new RuntimeException("Error accessing fields for class: " + clazz.getName(), e);
      }
      try {
        if (descriptor.getAttributeMapperDescriptor() == null) {
          mappers.add(null);
          continue;
        }
        String mappername = descriptor.getAttributeMapperDescriptor().getMapperName();
        Class<?> mapperClass = Class.forName(mappername);
        Constructor<?> mapperConstructor;
        try {
          mapperConstructor = mapperClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
          logger.error("Could not find no-arg constructor for mapper {}", mapperClass.getName());
          throw new RuntimeException(e);
        }
        AttributeMapper mapper;
        try {
          mapper = (AttributeMapper) mapperConstructor.newInstance();
          mappers.add(mapper);
        } catch (InstantiationException e) {
          logger.error("Failed to instantiate mapper {}", mapperClass.getName());
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          logger.error("Could not access constructor for mapper {}", mapperClass.getName());
          throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
          logger.error(
              "An exception was thrown by the constructor of mapper {}", mapperClass.getName());
          throw new RuntimeException(e);
        }
      } catch (Exception e) {
        throw new RuntimeException("Something went wrong, while accessing the mappers:", e);
      }
    }
    List<String> stringKeyParts = new ArrayList<>();
    for (int i = 0; i < fields.size(); i++) {
      String value = keyParts[i].toString();
      if (mappers.get(i) != null) {
        stringKeyParts.add(mappers.get(i).apply(value));
      } else {
        stringKeyParts.add(value);
      }
    }
    return stringKeyParts.toArray(String[]::new);
  }

  <T> byte[] toBuffer(final T entity) {
    return toJson(entity).getBytes(StandardCharsets.UTF_8);
  }

  <T> T fromBuffer(final byte[] buffer, final Class<T> clazz) {
    final String json = new String(buffer, StandardCharsets.UTF_8);
    logger.debug("Parsing entity from JSON: {}", json);
    return JSON.deserialize(json, clazz);
  }

  <T> String toJson(final T entity) {
    return JSON.serialize(entity);
  }

  public EntityKeyProvider getKeyProviderForClass(Class<?> clazz) {
    if (!keyProviders.containsKey(clazz)) {
      EntityKeyProvider provider = createEntityKeyProvider(clazz);
      keyProviders.put(clazz, provider);
    }
    return keyProviders.get(clazz);
  }

  /**
   * Generates a lambda which builds a CompositeKey for a given class instance
   * 
   * Using reflection we access the Field values which are given as primary keys,
   * and
   * with our mappers instances we map the values and with these we build the
   * Composite Key.
   * 
   * @param clazz the class of the entity
   * @return a lambda that creates a CompositeKey for an object instance
   * 
   */
  private EntityKeyProvider createEntityKeyProvider(Class<?> clazz) {
    try {
      EntityMeta em = metaInventory.getForClass(clazz);

      List<Field> fields = new ArrayList<>();
      List<AttributeMapper> mappers = new ArrayList<>();
      PrimaryKeyDescriptor pk = em.getPrimaryKeyDescriptor();
      List<AttributeDescriptor> pkAttributeDescriptors = pk.getAttributeDescriptiors();
      for (AttributeDescriptor descriptor : pkAttributeDescriptors) {
        try {
          Field field = clazz.getDeclaredField(descriptor.getAttrFieldName());
          field.setAccessible(true);
          fields.add(field);
        } catch (Exception e) {
          throw new RuntimeException("Error accessing fields for class: " + clazz.getName(), e);
        }
        try {
          if (descriptor.getAttributeMapperDescriptor() == null) {
            mappers.add(null);
            continue;
          }
          String mappername = descriptor.getAttributeMapperDescriptor().getMapperName();
          Class<?> mapperClass = Class.forName(mappername);
          Constructor<?> mapperConstructor;
          try {
            mapperConstructor = mapperClass.getDeclaredConstructor();
          } catch (NoSuchMethodException e) {
            logger.error("Could not find no-arg constructor for mapper {}", mapperClass.getName());
            throw new RuntimeException(e);
          }
          AttributeMapper mapper;
          try {
            mapper = (AttributeMapper) mapperConstructor.newInstance();
            mappers.add(mapper);
          } catch (InstantiationException e) {
            logger.error("Failed to instantiate mapper {}", mapperClass.getName());
            throw new RuntimeException(e);
          } catch (IllegalAccessException e) {
            logger.error("Could not access constructor for mapper {}", mapperClass.getName());
            throw new RuntimeException(e);
          } catch (InvocationTargetException e) {
            logger.error(
                "An exception was thrown by the constructor of mapper {}", mapperClass.getName());
            throw new RuntimeException(e);
          }
        } catch (Exception e) {
          throw new RuntimeException("Something went wrong, while accessing the mappers:", e);
        }
      }

      return (Object entity) -> {
        List<String> keyParts = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
          try {
            String value = fields.get(i).get(entity).toString();
            if (mappers.get(i) != null) {
              keyParts.add(mappers.get(i).apply(value));
            } else {
              keyParts.add(value);
            }
          } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access field value on entity", e);
          }
        }
        return new CompositeKey(clazz.getName(), keyParts).toString();
      };
    } catch (Exception e) {
      throw new RuntimeException("Failed to provide EntityKeyProvider for class: " + clazz.getName(), e);
    }
  }

  public String createCompositeKey(final Class<?> clazz) {
    return new CompositeKey(getType(clazz)).toString();
  }

  public String createCompositeKey(Class<?> clazz, Object... keyParts) {
    return new CompositeKey(getType(clazz), mapKeyPartsToString(clazz, keyParts)).toString();
  }
}
