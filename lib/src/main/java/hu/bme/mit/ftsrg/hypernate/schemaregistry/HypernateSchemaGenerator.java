/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

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
            .build();

    this.generator = new SchemaGenerator(config);
  }

  public byte[] generateSchema(Class<?> entityClass) {
    try {
      JsonNode schemaNode = generator.generateSchema(entityClass);
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
