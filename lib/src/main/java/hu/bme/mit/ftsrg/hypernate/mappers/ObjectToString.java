/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

public class ObjectToString implements AttributeMapper {

  @Override
  public String apply(Object object) {
    if (object == null) {
      throw new IllegalArgumentException(
          "The ObjectToString attribute mapper does not support null inputs");
    }
    return object.toString();
  }
}
