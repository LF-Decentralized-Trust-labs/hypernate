/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

public class LongZeroPadder implements AttributeMapper {

  private static final String FORMAT_STRING = "%0" + String.valueOf(Long.MAX_VALUE).length() + "d";

  @Override
  public String apply(Object object) {
    if (object instanceof Long longNumber && longNumber >= 0) {
      return String.format(LongZeroPadder.FORMAT_STRING, longNumber);
    }

    throw new IllegalArgumentException(
        "The LongZeroPadder attribute mapper only supports positive long number intputs");
  }
}
