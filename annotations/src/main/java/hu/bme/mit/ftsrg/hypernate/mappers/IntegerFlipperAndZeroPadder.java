/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

public class IntegerFlipperAndZeroPadder implements AttributeMapper {

  private static final String FORMAT_STRING =
      "%0" + String.valueOf(Integer.MAX_VALUE).length() + "d";

  @Override
  public String apply(Object object) {
    if (object instanceof Integer integer && integer >= 0) {
      return String.format(IntegerFlipperAndZeroPadder.FORMAT_STRING, Integer.MAX_VALUE - integer);
    }

    throw new IllegalArgumentException(
        "The IntegerFlipperAndZeroPadder attribute mapper only supports positive integer intputs");
  }
}
