/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

public class LongFlipperAndZeroPadder implements AttributeMapper {

  private static final String FORMAT_STRING = "%0" + String.valueOf(Long.MAX_VALUE).length() + "d";

  @Override
  public String apply(Object object) {
    if (object instanceof Long longNumber && longNumber >= 0) {
      return String.format(LongFlipperAndZeroPadder.FORMAT_STRING, Long.MAX_VALUE - longNumber);
    }

    throw new IllegalArgumentException(
        "The LongFlipperAndZeroPadder attribute mapper only supports positive long number intputs");
  }
}
