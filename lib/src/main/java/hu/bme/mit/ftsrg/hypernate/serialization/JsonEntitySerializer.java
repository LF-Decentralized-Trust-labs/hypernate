/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.serialization;

import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.nio.charset.StandardCharsets;

public class JsonEntitySerializer implements EntitySerializer {

  @Override
  public <T> byte[] toBuffer(final T entity) {
    return toJson(entity).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public <T> T fromBuffer(final byte[] buffer, final Class<T> clazz) {
    final String json = new String(buffer, StandardCharsets.UTF_8);
    return JSON.deserialize(json, clazz);
  }

  private <T> String toJson(final T entity) {
    return JSON.serialize(entity);
  }
}
