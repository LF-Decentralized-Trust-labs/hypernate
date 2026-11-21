/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import hu.bme.mit.ftsrg.hypernate.annotations.DefaultValue;
import hu.bme.mit.ftsrg.hypernate.annotations.RenamedFrom;
import java.lang.reflect.Field;

public class HypernateSchemaGenerator {
  private final ObjectMapper deterministicMapper;
  private final SchemaGenerator generator;

  public HypernateSchemaGenerator() {
    this.deterministicMapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    JacksonModule jacksonModule = new JacksonModule();
    SchemaGeneratorConfig config =
        new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
            .with(jacksonModule)
            .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
            .build();

    this.generator = new SchemaGenerator(config);
  }

  public byte[] generateSchema(Class<?> entityClass) {
    try {
      ObjectNode schemaNode = generator.generateSchema(entityClass);
      ObjectNode ruleNode = schemaNode.putObject("upcastMethods");
      ObjectNode renamesNode = ruleNode.putObject("renames");
      ObjectNode defaultNode = ruleNode.putObject("defaults");
      hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion schemaVersion =
          entityClass.getAnnotation(hu.bme.mit.ftsrg.hypernate.annotations.SchemaVersion.class);
      int version = schemaVersion != null ? schemaVersion.value() : 1;
      for (Field field : entityClass.getDeclaredFields()) {
        RenamedFrom renamedFrom = field.getAnnotation(RenamedFrom.class);
        if (renamedFrom != null && version == renamedFrom.since()) {
          renamesNode.put(field.getName(), renamedFrom.value());
        }
        DefaultValue defaultValue = field.getAnnotation(DefaultValue.class);
        if (defaultValue != null && version == defaultValue.since()) {
          JsonNode defaultJson = deterministicMapper.readTree(defaultValue.value());
          defaultNode.set(field.getName(), defaultJson);
        }
      }
      Object mapRepresentation = deterministicMapper.treeToValue(schemaNode, Object.class);
      return deterministicMapper.writeValueAsBytes(mapRepresentation);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not generate schema", e);
    }
  }

  public JsonNode parseSchema(byte[] schemaBytes) {
    try {
      return deterministicMapper.readTree(schemaBytes);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Could not parse schema bytes", e);
    }
  }
}
