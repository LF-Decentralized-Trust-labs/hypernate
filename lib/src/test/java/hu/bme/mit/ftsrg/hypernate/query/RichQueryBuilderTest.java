/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddlewareChain;
import hu.bme.mit.ftsrg.hypernate.middleware.WriteBackCachedStubMiddleware;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
class RichQueryBuilderTest {

  private static final String OWNER_FIELD = "owner";
  private static final String ASSET_ID_FIELD = "assetId";
  private static final String COLOR_FIELD = "color";
  private static final String SIZE_FIELD = "size";
  private static final String VALUE_FIELD = "value";
  private static final TestAsset asset = new TestAsset("Alice", 7, "blue", 12, 42);
  private static final byte[] ASSET_BUFFER;

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

  @PrimaryKey({@AttributeInfo(name = OWNER_FIELD), @AttributeInfo(name = ASSET_ID_FIELD)})
  private record TestAsset(
      String owner, Integer assetId, String color, Integer size, Integer value) {}

  @Nested
  class when_executing_rich_query {

    @Test
    void given_single_predicate_then_build_eq_selector_json() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));

      registry.richQuery(TestAsset.class).where(COLOR_FIELD).is("blue").execute();

      final ArgumentCaptor<String> selector = ArgumentCaptor.forClass(String.class);
      then(stub).should().getQueryResult(selector.capture());
      assertEquals("{\"selector\":{\"color\":{\"$eq\":\"blue\"}}}", selector.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_multiple_predicates_chained_with_and_then_build_combined_selector_json() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));

      registry
          .richQuery(TestAsset.class)
          .where(COLOR_FIELD)
          .is("blue")
          .and(OWNER_FIELD)
          .is("Alice")
          .execute();

      final ArgumentCaptor<String> selector = ArgumentCaptor.forClass(String.class);
      then(stub).should().getQueryResult(selector.capture());
      assertEquals(
          "{\"selector\":{\"color\":{\"$eq\":\"blue\"},\"owner\":{\"$eq\":\"Alice\"}}}",
          selector.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_in_predicate_then_build_in_selector_json() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));

      registry.richQuery(TestAsset.class).where(OWNER_FIELD).in("Alice", "Bob").execute();

      final ArgumentCaptor<String> selector = ArgumentCaptor.forClass(String.class);
      then(stub).should().getQueryResult(selector.capture());
      assertEquals("{\"selector\":{\"owner\":{\"$in\":[\"Alice\",\"Bob\"]}}}", selector.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_greater_than_predicate_then_build_gt_selector_json() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));

      registry.richQuery(TestAsset.class).where(SIZE_FIELD).greaterThan(10).execute();

      final ArgumentCaptor<String> selector = ArgumentCaptor.forClass(String.class);
      then(stub).should().getQueryResult(selector.capture());
      assertEquals("{\"selector\":{\"size\":{\"$gt\":10}}}", selector.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_sort_clause_then_include_sort_in_selector_json() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));

      registry
          .richQuery(TestAsset.class)
          .where(COLOR_FIELD)
          .is("blue")
          .sortBy(VALUE_FIELD, SortOrder.DESC)
          .execute();

      final ArgumentCaptor<String> selector = ArgumentCaptor.forClass(String.class);
      then(stub).should().getQueryResult(selector.capture());
      assertEquals(
          "{\"selector\":{\"color\":{\"$eq\":\"blue\"}},\"sort\":[{\"value\":\"desc\"}]}",
          selector.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_limit_clause_then_include_limit_in_selector_json() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));

      registry.richQuery(TestAsset.class).where(COLOR_FIELD).is("blue").limit(50).execute();

      final ArgumentCaptor<String> selector = ArgumentCaptor.forClass(String.class);
      then(stub).should().getQueryResult(selector.capture());
      assertEquals(
          "{\"selector\":{\"color\":{\"$eq\":\"blue\"}},\"limit\":50}", selector.getValue());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_invalid_field_name_then_throw_illegal_argument_exception() {
      assertThrows(
          IllegalArgumentException.class,
          () -> registry.richQuery(TestAsset.class).where("missingField").is("blue").execute());

      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_dirty_write_back_cache_then_log_stale_read_warning() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));
      final WriteBackCachedStubMiddleware cache = new WriteBackCachedStubMiddleware();
      final ChaincodeStub cachedStub =
          StubMiddlewareChain.builder(stub).push(cache).build().getFirst();
      cachedStub.putState("dirty-key", new byte[] {1});

      final String logs =
          captureStandardError(
              () ->
                  new Registry(cachedStub)
                      .richQuery(TestAsset.class)
                      .where(COLOR_FIELD)
                      .is("blue")
                      .execute());

      assertTrue(logs.contains(RichQueryBuilderImpl.STALE_READ_WARNING_MESSAGE));
      then(stub).should().getQueryResult(anyString());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_acknowledged_stale_reads_then_do_not_log_stale_read_warning() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));
      final WriteBackCachedStubMiddleware cache = new WriteBackCachedStubMiddleware();
      final ChaincodeStub cachedStub =
          StubMiddlewareChain.builder(stub).push(cache).build().getFirst();
      cachedStub.putState("dirty-key", new byte[] {1});

      final String logs =
          captureStandardError(
              () ->
                  new Registry(cachedStub)
                      .richQuery(TestAsset.class)
                      .where(COLOR_FIELD)
                      .is("blue")
                      .acknowledgeStaleReads()
                      .execute());

      assertFalse(logs.contains(RichQueryBuilderImpl.STALE_READ_WARNING_MESSAGE));
      then(stub).should().getQueryResult(anyString());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_empty_result_set_then_return_empty_list() {
      given(stub.getQueryResult(anyString())).willReturn(mockIterator(List.of()));

      final List<TestAsset> results =
          registry.richQuery(TestAsset.class).where(COLOR_FIELD).is("blue").execute();

      assertTrue(results.isEmpty());
      then(stub).should().getQueryResult(anyString());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void given_key_values_then_deserialize_entities_from_query_results() {
      given(stub.getQueryResult(anyString()))
          .willReturn(mockIterator(List.of(mockKeyValue("asset-1", ASSET_BUFFER))));

      final List<TestAsset> results =
          registry.richQuery(TestAsset.class).where(OWNER_FIELD).is("Alice").execute();

      assertEquals(List.of(asset), results);
      then(stub).should().getQueryResult(anyString());
      verifyNoMoreInteractions(stub);
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

  private static String captureStandardError(final Runnable action) {
    final PrintStream originalErr = System.err;
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      action.run();
      capture.flush();
    } finally {
      System.setErr(originalErr);
    }

    return buffer.toString(StandardCharsets.UTF_8);
  }
}
