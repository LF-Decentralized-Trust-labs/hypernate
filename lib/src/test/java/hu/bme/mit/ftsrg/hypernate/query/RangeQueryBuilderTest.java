/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.mappers.IntegerZeroPadder;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class RangeQueryBuilderTest {

  private static final String OWNER_FIELD = "owner";
  private static final String ASSET_ID_FIELD = "assetId";
  private static final String COLOR_FIELD = "color";
  private static final TestAsset asset = new TestAsset("Alice", 7, "blue");
  private static final byte[] ASSET_BUFFER;
  private static final String ENTITY_TYPE = TestAsset.class.getName().toUpperCase(Locale.ROOT);
  private static final String PADDED_ASSET_ID = String.format("%010d", asset.assetId());

  static {
    try {
      ASSET_BUFFER = JSON.serialize(asset).getBytes(StandardCharsets.UTF_8);
    } catch (SerializationException e) {
      throw new RuntimeException(e);
    }
  }

  @Mock private ChaincodeStub stub;

  private Registry registry;

  @BeforeEach
  void setup() {
    registry = new Registry(stub);
  }

  @PrimaryKey({
    @AttributeInfo(name = OWNER_FIELD),
    @AttributeInfo(name = ASSET_ID_FIELD, mapper = IntegerZeroPadder.class)
  })
  private record TestAsset(String owner, Integer assetId, String color) {}

  @Nested
  class when_executing_range_query {

    @Test
    void given_single_key_part_then_call_partial_composite_key_scan_with_prefix() {
      given(stub.getStateByPartialCompositeKey(anyString(), any()))
          .willReturn(mockIterator(List.of()));

      registry.rangeQuery(TestAsset.class).whereKey(OWNER_FIELD).is("Alice").execute();

      final ArgumentCaptor<String[]> keyParts = ArgumentCaptor.forClass(String[].class);
      then(stub).should().getStateByPartialCompositeKey(eq(ENTITY_TYPE), keyParts.capture());
      assertArrayEquals(new String[] {"Alice"}, keyParts.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_multiple_key_parts_in_correct_order_then_call_partial_composite_key_scan() {
      given(stub.getStateByPartialCompositeKey(eq(ENTITY_TYPE), eq("Alice"), eq(PADDED_ASSET_ID)))
          .willReturn(mockIterator(List.of(mockKeyValue("asset-1", ASSET_BUFFER))));

      final List<TestAsset> results =
          registry
              .rangeQuery(TestAsset.class)
              .whereKey(OWNER_FIELD)
              .is("Alice")
              .whereKey(ASSET_ID_FIELD)
              .is(asset.assetId())
              .execute();

      final ArgumentCaptor<String[]> keyParts = ArgumentCaptor.forClass(String[].class);
      then(stub).should().getStateByPartialCompositeKey(eq(ENTITY_TYPE), keyParts.capture());
      assertArrayEquals(new String[] {"Alice", PADDED_ASSET_ID}, keyParts.getValue());
      assertEquals(List.of(asset), results);
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_key_parts_out_of_primary_key_order_then_throw_illegal_argument_exception() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              registry
                  .rangeQuery(TestAsset.class)
                  .whereKey(ASSET_ID_FIELD)
                  .is(asset.assetId())
                  .whereKey(OWNER_FIELD)
                  .is("Alice")
                  .execute());

      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_field_not_in_primary_key_then_throw_illegal_argument_exception() {
      assertThrows(
          IllegalArgumentException.class,
          () -> registry.rangeQuery(TestAsset.class).whereKey(COLOR_FIELD).is("blue").execute());

      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_empty_result_set_then_return_empty_list() {
      given(stub.getStateByPartialCompositeKey(anyString(), any()))
          .willReturn(mockIterator(List.of()));

      final List<TestAsset> results =
          registry.rangeQuery(TestAsset.class).whereKey(OWNER_FIELD).is("Alice").execute();

      assertTrue(results.isEmpty());
      final ArgumentCaptor<String[]> keyParts = ArgumentCaptor.forClass(String[].class);
      then(stub).should().getStateByPartialCompositeKey(eq(ENTITY_TYPE), keyParts.capture());
      assertArrayEquals(new String[] {"Alice"}, keyParts.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
        "rangeQuery with no whereKey() throws IllegalArgumentException")
    void execute_withNoKeyConstraints_throwsIllegalArgumentException() {
      // given
      var builder = registry.rangeQuery(TestAsset.class);

      // when / then
      assertThrows(
          IllegalArgumentException.class,
          builder::execute,
          "Expected IllegalArgumentException for unconstrained rangeQuery");

      // verify no stub interaction occurred
      verifyNoInteractions(stub);
    }
  }

  private static QueryResultsIterator<KeyValue> mockIterator(final List<KeyValue> keyValues) {
    return new QueryResultsIterator<>() {
      @Override
      public void close() {}

      @Override
      public Iterator<KeyValue> iterator() {
        return keyValues.iterator();
      }
    };
  }

  private static KeyValue mockKeyValue(final String key, final byte[] value) {
    return new KeyValue() {
      @Override
      public String getKey() {
        return key;
      }

      @Override
      public byte[] getValue() {
        return value;
      }

      @Override
      public String getStringValue() {
        return Arrays.toString(value);
      }
    };
  }
}
