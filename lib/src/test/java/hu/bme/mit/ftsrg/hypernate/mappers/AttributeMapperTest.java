/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AttributeMapperTest {

  @Nested
  class ObjectToStringTest {
    private final ObjectToString mapper = new ObjectToString();

    @Test
    void given_valid_object_then_returns_string_representation() {
      assertEquals("hello", mapper.apply("hello"));
      assertEquals("123", mapper.apply(123));
    }

    @Test
    void given_null_then_throws_illegal_argument_exception() {
      assertThrows(IllegalArgumentException.class, () -> mapper.apply(null));
    }
  }

  @Nested
  class IntegerZeroPadderTest {
    private final IntegerZeroPadder mapper = new IntegerZeroPadder();

    @Test
    void given_positive_integer_then_pads_with_leading_zeros() {
      String result = mapper.apply(9);
      assertEquals("0000000009", result);
      assertEquals(10, result.length());
    }

    @Test
    void given_zero_then_pads_correctly() {
      String result = mapper.apply(0);
      assertEquals("0000000000", result);
    }

    @Test
    void given_max_integer_then_formats_without_extra_padding() {
      String result = mapper.apply(Integer.MAX_VALUE);
      assertEquals(String.valueOf(Integer.MAX_VALUE), result);
    }

    @Test
    void given_negative_integer_or_invalid_type_then_throws_exception() {
      assertThrows(IllegalArgumentException.class, () -> mapper.apply(-1));
      assertThrows(IllegalArgumentException.class, () -> mapper.apply("123"));
      assertThrows(IllegalArgumentException.class, () -> mapper.apply(null));
    }
  }

  @Nested
  class IntegerFlipperAndZeroPadderTest {
    private final IntegerFlipperAndZeroPadder mapper = new IntegerFlipperAndZeroPadder();

    @Test
    void given_ascending_integers_then_produces_descending_padded_strings() {
      String val0 = mapper.apply(0);
      String val1 = mapper.apply(1);
      String valMax = mapper.apply(Integer.MAX_VALUE);

      assertTrue(val0.compareTo(val1) > 0);
      assertEquals("0000000000", valMax);
    }

    @Test
    void given_negative_integer_or_invalid_type_then_throws_exception() {
      assertThrows(IllegalArgumentException.class, () -> mapper.apply(-1));
      assertThrows(IllegalArgumentException.class, () -> mapper.apply("123"));
    }
  }

  @Nested
  class LongZeroPadderTest {
    private final LongZeroPadder mapper = new LongZeroPadder();

    @Test
    void given_positive_long_then_pads_with_leading_zeros() {
      String result = mapper.apply(42L);
      assertEquals(String.valueOf(Long.MAX_VALUE).length(), result.length());
      assertTrue(result.endsWith("42"));
    }

    @Test
    void given_negative_long_then_throws_exception() {
      assertThrows(IllegalArgumentException.class, () -> mapper.apply(-5L));
      assertThrows(IllegalArgumentException.class, () -> mapper.apply(100));
    }
  }

  @Nested
  class LongFlipperAndZeroPadderTest {
    private final LongFlipperAndZeroPadder mapper = new LongFlipperAndZeroPadder();

    @Test
    void given_ascending_longs_then_produces_descending_padded_strings() {
      String val0 = mapper.apply(0L);
      String val1 = mapper.apply(1L);
      String valMax = mapper.apply(Long.MAX_VALUE);

      assertTrue(val0.compareTo(val1) > 0);
      assertEquals(String.format("%0" + String.valueOf(Long.MAX_VALUE).length() + "d", 0L), valMax);
    }

    @Test
    void given_negative_long_then_throws_exception() {
      assertThrows(IllegalArgumentException.class, () -> mapper.apply(-1L));
    }
  }
}
