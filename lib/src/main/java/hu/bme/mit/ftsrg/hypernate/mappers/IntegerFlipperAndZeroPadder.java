/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

/**
 * Subtracts the attribute value from {@value Integer#MAX_VALUE} ({@link Integer#MAX_VALUE}) and
 * then pads the resulting value to the length {@value Integer#MAX_VALUE}'s {@link String}
 * representation.
 *
 * <p>This mapper can be useful if you know that you will want the assets enumerated in reverse
 * order most of the time.
 *
 * @see IntegerZeroPadder
 */
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
