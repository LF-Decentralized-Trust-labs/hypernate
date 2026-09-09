/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.KeyClass;
import hu.bme.mit.ftsrg.hypernate.annotations.KeyOrder;
import hu.bme.mit.ftsrg.hypernate.annotations.MapperInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.registry.MissingOrderException;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityMetadataInventory {
  private static Map<String, EntityMeta> data = new ConcurrentHashMap<>();
  private static final Logger logger = LoggerFactory.getLogger(EntityMetadataInventory.class);

  private static void add(EntityMeta meta) {
    data.put(meta.getClassName(), meta);
  }

  public EntityMeta getForClass(Class<?> clazz) {
    return data.get(clazz.getName());
  }

  /**
   * Initializes the metadata registry by scanning the classpath for annotated classes. Searches for
   * classes marked with {@link PrimaryKey} or {@link KeyClass} annotations. For each discovered
   * class, it generates the corresponding {@link EntityMeta} and stores it in the internal metadata
   * inventory.
   */
  static {
    try (ScanResult result =
        new ClassGraph()
            .enableClassInfo()
            .enableExternalClasses()
            .ignoreClassVisibility()
            .enableAnnotationInfo()
            .scan()) {
      ClassInfoList primaryKeyInfo = result.getClassesWithAnnotation(PrimaryKey.class);
      ClassInfoList keyClassInfo = result.getClassesWithAnnotation(KeyClass.class);
      if (primaryKeyInfo.isEmpty()) {
        logger.info("There are no classes with PrimaryKey annotation.");
      } else {
        primaryKeyInfo.forEach(
            info -> {
              generateMetadataFromPrimaryKey(info);
            });
      }
      if (keyClassInfo.isEmpty()) {
        logger.info("There are no classes with KeyClass annotation.");
      } else {
        keyClassInfo.forEach(
            info -> {
              generateMetadataFromKeyClass(info);
            });
      }
    } catch (Exception e) {
      logger.error("Failed to load classpaths", e);
    }
  }

  /**
   * Generates and registers metadata for a class annotated with {@link PrimaryKey}.
   *
   * <p>This method extracts attribute and mapper information directly from the {@code @PrimaryKey}
   * annotation's value array.
   *
   * @param info the {@link ClassInfo} of the annotated class
   */
  private static void generateMetadataFromPrimaryKey(ClassInfo info) {
    Class<?> clazz = info.loadClass();
    PrimaryKey pk = clazz.getAnnotation(PrimaryKey.class);
    if (pk == null) {
      throw new IllegalStateException(
          "Missing @PrimaryKey runtime annotation on class " + clazz.getName());
    }
    EntityMeta meta = new EntityMeta(clazz.getName(), null);
    PrimaryKeyDescriptor pKeyDescriptor = new PrimaryKeyDescriptor(meta);
    AttributeInfo[] attrinfos = pk.value();
    for (AttributeInfo attrinfo : attrinfos) {
      String name = attrinfo.name();
      String mappername = attrinfo.mapper().getName();
      AttributeDescriptor attributeDescriptor = new AttributeDescriptor(pKeyDescriptor, name, null);
      AttributeMapperDescriptor mapperDescriptor =
          new AttributeMapperDescriptor(attributeDescriptor, mappername);
      attributeDescriptor.setAttributeMapperDescriptor(mapperDescriptor);
      pKeyDescriptor.add(attributeDescriptor);
    }
    meta.setPrimaryKeyDescriptor(pKeyDescriptor);
    add(meta);
  }

  /**
   * Generates and registers metadata based on a {@link KeyClass} annotation.
   *
   * <p>Unlike {@code PrimaryKey}, this method treats the annotated class as a template for a target
   * entity. It uses reflection to scan the fields of the annotated class to build the primary key
   * descriptor for the referenced entity class.
   *
   * @param info the {@link ClassInfo} of the class containing the {@code @KeyClass} annotation
   */
  private static void generateMetadataFromKeyClass(ClassInfo info) {
    Class<?> clazz = info.loadClass();
    KeyClass key = clazz.getAnnotation(KeyClass.class);
    if (key == null) {
      throw new IllegalStateException(
          "Missing @PrimaryKey runtime annotation on class " + clazz.getName());
    }
    Class<?> pointed = key.value();
    EntityMeta meta = new EntityMeta(pointed.getName(), null);
    PrimaryKeyDescriptor pKeyDescriptor = new PrimaryKeyDescriptor(meta);
    Field[] fields = clazz.getDeclaredFields();
    Arrays.sort(
        fields,
        Comparator.comparingInt(
            f -> {
              KeyOrder o = f.getAnnotation(KeyOrder.class);
              if (o == null)
                throw new MissingOrderException(
                    "There is at least one Field without a specified Order");
              return o.value();
            }));
    for (Field field : fields) {
      String name = field.getName();
      AttributeDescriptor attributeDescriptor = new AttributeDescriptor(pKeyDescriptor, name, null);
      if (field.isAnnotationPresent(MapperInfo.class)) {
        MapperInfo mapperinfo = field.getAnnotation(MapperInfo.class);
        if (mapperinfo != null) {
          String mapperName = mapperinfo.value().getName();
          AttributeMapperDescriptor mapperDescriptor =
              new AttributeMapperDescriptor(attributeDescriptor, mapperName);
          attributeDescriptor.setAttributeMapperDescriptor(mapperDescriptor);
        }
      }
      pKeyDescriptor.add(attributeDescriptor);
    }
    meta.setPrimaryKeyDescriptor(pKeyDescriptor);
    add(meta);
  }
}
