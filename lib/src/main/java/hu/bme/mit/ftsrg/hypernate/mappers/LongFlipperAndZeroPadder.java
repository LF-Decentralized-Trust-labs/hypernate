/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

/**
 * Subtracts the attribute value from {@value Long#MAX_VALUE} ({@link Long#MAX_VALUE}) and then pads
 * the resulting value to the length {@value Long#MAX_VALUE}'s {@link String} representation.
 *
 * <p>This mapper can be useful if you know that you will want the assets enumerated in reverse
 * order most of the time.
 *
 * @see LongZeroPadder
 */
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
