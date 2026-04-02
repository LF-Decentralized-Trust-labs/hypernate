/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.integration;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import lombok.experimental.FieldNameConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegistryAndJsonIntegrationTest {

  @FieldNameConstants
  @PrimaryKey({
    @AttributeInfo(name = IntegrationEntity.Fields.id),
    @AttributeInfo(name = IntegrationEntity.Fields.kind)
  })
  private record IntegrationEntity(String id, String kind, String payload) {}

  @Test
  @DisplayName("Serialize and deserialize annotated entity consistently")
  void shouldRoundTripEntitySerialization() throws SerializationException {
    IntegrationEntity original = new IntegrationEntity("42", "demo", "sample");

    String json = JSON.serialize(original);
    IntegrationEntity restored = JSON.deserialize(json, IntegrationEntity.class);

    assertThat(restored).isEqualTo(original);
  }
}
