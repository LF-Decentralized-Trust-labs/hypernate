/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

public class IntegerZeroPadder implements AttributeMapper {

  private static final String FORMAT_STRING =
      "%0" + String.valueOf(Integer.MAX_VALUE).length() + "d";

  @Override
  public String apply(Object object) {
    if (object instanceof Integer integer && integer >= 0) {
      return String.format(IntegerZeroPadder.FORMAT_STRING, integer);
    }

    throw new IllegalArgumentException(
        "The IntegerZeroPadder attribute mapper only supports positive integer intputs");
  }
}
