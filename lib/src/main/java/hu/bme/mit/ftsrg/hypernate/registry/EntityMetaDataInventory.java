package hu.bme.mit.ftsrg.hypernate.registry;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.LoggerFactory;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.slf4j.Logger;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.KeyClass;
import hu.bme.mit.ftsrg.hypernate.annotations.MapperInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.Order;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

public class EntityMetaDataInventory {
    private static Set<EntityMeta> data = new HashSet<>();
    private static final Logger logger = LoggerFactory.getLogger(EntityMetaDataInventory.class);

    public EntityMetaDataInventory() {
    }

    public static void add(EntityMeta meta) {
        data.add(meta);
    }

    public EntityMeta getForClass(Class<?> clazz) {
        for (EntityMeta em : data) {
            if (em.getClassName().equals(clazz.getName())) {
                return em;
            }
        }
        return null;
    }

    /**
     * Initializes the metadata registry by scanning the classpath for annotated
     * classes.
     * Searches for classes marked with {@link PrimaryKey} or {@link KeyClass}
     * annotations.
     * For each discovered class, it generates the corresponding {@link EntityMeta}
     * and stores it in the internal metadata inventory.
     * 
     */
    static {
        try (ScanResult result = new ClassGraph().enableClassInfo().enableExternalClasses().ignoreClassVisibility()
                .enableAnnotationInfo().scan()) {
            ClassInfoList primaryKeyInfo = result.getClassesWithAnnotation(PrimaryKey.class);
            ClassInfoList keyClassInfo = result.getClassesWithAnnotation(KeyClass.class);
            if (primaryKeyInfo.isEmpty()) {
                logger.info("There are no classes with PrimaryKey annotation.");
            } else {
                primaryKeyInfo.forEach(info -> {
                    generateMetadataFromPrimaryKey(info);
                });
            }
            if (keyClassInfo.isEmpty()) {
                logger.info("There are no classes with KeyClass annotation.");
            } else {
                keyClassInfo.forEach(info -> {
                    generateMetadataFromKeyClass(info);
                });
            }
        } catch (Exception e) {
            logger.error("Failed to load classpaths", e);
        }
    }

    /**
     * Generates and registers metadata for a class annotated with
     * {@link PrimaryKey}.
     * 
     * This method extracts attribute and mapper information directly from the
     * {@code @PrimaryKey} annotation's value array.
     * 
     * @param info the {@link ClassInfo} of the annotated class
     */

    public static void generateMetadataFromPrimaryKey(ClassInfo info) {
        Class<?> classes = info.loadClass();
        PrimaryKey pk = classes.getAnnotation(PrimaryKey.class);
        if (pk == null) {
            return;
        }
        EntityMeta meta = new EntityMeta(classes.getName(), null);
        PrimaryKeyDescriptor pKeyDescriptor = new PrimaryKeyDescriptor(meta);
        AttributeInfo[] attrinfos = pk.value();
        for (AttributeInfo attrinfo : attrinfos) {
            String name = attrinfo.name();
            String mappername = attrinfo.mapper().getName();
            AttributeDescriptor attributeDescriptor = new AttributeDescriptor(pKeyDescriptor, name, null);
            AttributeMapperDescriptor mapperDescriptor = new AttributeMapperDescriptor(attributeDescriptor, mappername);
            attributeDescriptor.setAttributeMapperDescriptor(mapperDescriptor);
            pKeyDescriptor.add(attributeDescriptor);
        }
        meta.setPrimaryKeyDescriptor(pKeyDescriptor);
        add(meta);
    }

    /**
     * Generates and registers metadata based on a {@link KeyClass} annotation.
     * 
     * Unlike {@code PrimaryKey}, this method treats the annotated class as a
     * template
     * for a target entity. It uses reflection to scan the fields of the annotated
     * class to build the primary key descriptor for the referenced entity class.
     * 
     * @param info the {@link ClassInfo} of the class containing the
     *             {@code @KeyClass} annotation
     */

    public static void generateMetadataFromKeyClass(ClassInfo info) {
        Class<?> classes = info.loadClass();
        KeyClass key = classes.getAnnotation(KeyClass.class);
        if (key == null) {
            return;
        }
        Class<?> pointed = key.value();
        EntityMeta meta = new EntityMeta(pointed.getName(), null);
        PrimaryKeyDescriptor pKeyDescriptor = new PrimaryKeyDescriptor(meta);
        Field[] fields = classes.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isAnnotationPresent(Order.class)) {
                throw new ChaincodeException(
                        "There is at least one Field without order in the class annotated with KeyClass");
            }
        }
        Arrays.sort(fields, Comparator.comparingInt(f -> f.getAnnotation(Order.class).value()));
        for (Field field : fields) {
            String name = field.getName();
            AttributeDescriptor attributeDescriptor = new AttributeDescriptor(pKeyDescriptor, name, null);
            if (field.isAnnotationPresent(MapperInfo.class)) {
                MapperInfo mapperinfo = field.getAnnotation(MapperInfo.class);
                if (mapperinfo != null) {
                    String mapperName = mapperinfo.value().getName();
                    AttributeMapperDescriptor mapperDescriptor = new AttributeMapperDescriptor(attributeDescriptor,
                            mapperName);
                    attributeDescriptor.setAttributeMapperDescriptor(mapperDescriptor);
                }
            }
            pKeyDescriptor.add(attributeDescriptor);
        }
        meta.setPrimaryKeyDescriptor(pKeyDescriptor);
        add(meta);
    }

}
