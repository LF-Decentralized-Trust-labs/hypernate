/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import hu.bme.mit.ftsrg.hypernate.contract.HypernateContract;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class LoggingStubMiddlewareTest {

  @Mock ChaincodeStub fabricStub;
  @Mock Logger logger;
  @Mock LoggingEventBuilder loggingEventBuilder;

  LoggingStubMiddleware middleware;

  @BeforeEach
  void setUp() {
    lenient().when(logger.atLevel(any(Level.class))).thenReturn(loggingEventBuilder);
    middleware =
        new LoggingStubMiddleware(
            logger,
            Level.DEBUG,
            new LogConfig(KeyLogMode.FULL_KEY, ValueLogMode.VALUE_UTF8),
            Map.of());
    middleware.nextStub = fabricStub;
  }

  @Test
  void getState_delegates_to_next_stub_correctly() {
    byte[] expected = "value".getBytes(StandardCharsets.UTF_8);
    when(fabricStub.getState("key1")).thenReturn(expected);

    byte[] result = middleware.getState("key1");

    assertArrayEquals(expected, result);
    verify(fabricStub).getState("key1");
  }

  @Test
  void putState_delegates_to_next_stub_correctly() {
    byte[] value = "value".getBytes(StandardCharsets.UTF_8);

    middleware.putState("key1", value);

    verify(fabricStub).putState("key1", value);
  }

  @Test
  void delState_delegates_to_next_stub_correctly() {
    middleware.delState("key1");

    verify(fabricStub).delState("key1");
  }

  @Test
  void configured_public_logging_can_include_full_keys_and_utf8_values() {
    byte[] value = "super-secret".getBytes(StandardCharsets.UTF_8);
    when(fabricStub.getState("asset-123")).thenReturn(value);

    middleware.getState("asset-123");

    verify(loggingEventBuilder).log("getState: key='asset-123'");
    verify(loggingEventBuilder).log("getState result: key='asset-123', value='super-secret'");
  }

  @Test
  void default_configuration_keeps_public_values_out_of_logs() {
    LoggingStubMiddleware safeDefaults = new LoggingStubMiddleware(logger, Level.DEBUG);
    safeDefaults.nextStub = fabricStub;
    when(fabricStub.getState("asset-123"))
        .thenReturn("top-secret".getBytes(StandardCharsets.UTF_8));

    safeDefaults.getState("asset-123");

    verify(loggingEventBuilder).log("getState: key{kind=simple,length=9}");
    verify(loggingEventBuilder).log("getState result: key{kind=simple,length=9}");
    verify(loggingEventBuilder, never()).log(contains("top-secret"));
    verify(loggingEventBuilder, never()).log(contains("asset-123"));
  }

  @Test
  void default_configuration_hashes_private_keys_and_hides_private_values() {
    LoggingStubMiddleware safeDefaults = new LoggingStubMiddleware(logger, Level.DEBUG);
    safeDefaults.nextStub = fabricStub;
    when(fabricStub.getPrivateData("privateMedical", "patient-john-doe"))
        .thenReturn("diagnosis".getBytes(StandardCharsets.UTF_8));

    safeDefaults.getPrivateData("privateMedical", "patient-john-doe");

    verify(loggingEventBuilder)
        .log(startsWith("getPrivateData: collection='privateMedical', key{bytes=16,sha256='"));
    verify(loggingEventBuilder)
        .log(
            startsWith(
                "getPrivateData result: collection='privateMedical', key{bytes=16,sha256='"));
    verify(loggingEventBuilder, never()).log(contains("patient-john-doe"));
    verify(loggingEventBuilder, never()).log(contains("diagnosis"));
  }

  @Test
  void collection_overrides_apply_to_private_data_logging() {
    LoggingStubMiddleware configured =
        new LoggingStubMiddleware(
            logger,
            Level.DEBUG,
            new LogConfig(KeyLogMode.FULL_KEY, ValueLogMode.VALUE_UTF8),
            Map.of(
                "privateMedical", new LogConfig(KeyLogMode.KEY_HASH, ValueLogMode.VALUE_METADATA)));
    configured.nextStub = fabricStub;
    when(fabricStub.getPrivateData("privateMedical", "patient-john-doe"))
        .thenReturn("diagnosis".getBytes(StandardCharsets.UTF_8));

    configured.getPrivateData("privateMedical", "patient-john-doe");

    verify(loggingEventBuilder)
        .log(startsWith("getPrivateData: collection='privateMedical', key{bytes=16,sha256='"));
    verify(loggingEventBuilder)
        .log(
            startsWith(
                "getPrivateData result: collection='privateMedical', key{bytes=16,sha256='"));
    verify(loggingEventBuilder, never()).log(contains("patient-john-doe"));
    verify(loggingEventBuilder, never()).log(contains("diagnosis"));
  }

  @Test
  void annotation_configuration_is_applied_when_contract_builds_middlewares() throws Exception {
    LoggingStubMiddleware configured =
        (LoggingStubMiddleware) new ConfiguredContract().initMiddlewares(fabricStub).getFirst();

    assertEquals(
        new LogConfig(KeyLogMode.FULL_KEY, ValueLogMode.VALUE_UTF8),
        getField(configured, "publicDefaultConfig"));
    assertEquals(
        new LogConfig(KeyLogMode.FULL_KEY, ValueLogMode.VALUE_UTF8),
        getField(configured, "privateDefaultConfig"));
    @SuppressWarnings("unchecked")
    Map<String, LogConfig> collectionConfigs =
        (Map<String, LogConfig>) getField(configured, "collectionConfigs");
    assertEquals(
        new LogConfig(KeyLogMode.KEY_HASH, ValueLogMode.VALUE_METADATA),
        collectionConfigs.get("privateMedical"));
  }

  @Test
  void relevant_stub_methods_are_overridden_locally_and_not_left_to_delegate() throws Exception {
    assertOverridden("getState", String.class);
    assertOverridden("getStringState", String.class);
    assertOverridden("putState", String.class, byte[].class);
    assertOverridden("putStringState", String.class, String.class);
    assertOverridden("setStateValidationParameter", String.class, byte[].class);
    assertOverridden("delState", String.class);
    assertOverridden("getStateByRange", String.class, String.class);
    assertOverridden(
        "getStateByRangeWithPagination", String.class, String.class, int.class, String.class);
    assertOverridden("getStateByPartialCompositeKey", String.class);
    assertOverridden("getStateByPartialCompositeKey", String.class, String[].class);
    assertOverridden("getStateByPartialCompositeKey", CompositeKey.class);
    assertOverridden(
        "getStateByPartialCompositeKeyWithPagination", CompositeKey.class, int.class, String.class);
    assertOverridden("getQueryResult", String.class);
    assertOverridden("getQueryResultWithPagination", String.class, int.class, String.class);
    assertOverridden("getHistoryForKey", String.class);
    assertOverridden("getPrivateData", String.class, String.class);
    assertOverridden("putPrivateData", String.class, String.class, byte[].class);
    assertOverridden("putPrivateData", String.class, String.class, String.class);
    assertOverridden("setPrivateDataValidationParameter", String.class, String.class, byte[].class);
    assertOverridden("delPrivateData", String.class, String.class);
    assertOverridden("purgePrivateData", String.class, String.class);
    assertOverridden("getPrivateDataHash", String.class, String.class);
    assertOverridden("getPrivateDataValidationParameter", String.class, String.class);
    assertOverridden("getPrivateDataByRange", String.class, String.class, String.class);
    assertOverridden("getPrivateDataByPartialCompositeKey", String.class, String.class);
    assertOverridden("getPrivateDataByPartialCompositeKey", String.class, CompositeKey.class);
    assertOverridden(
        "getPrivateDataByPartialCompositeKey", String.class, String.class, String[].class);
    assertOverridden("getPrivateDataQueryResult", String.class, String.class);
    assertOverridden("invokeChaincode", String.class, List.class, String.class);
    assertOverridden("setEvent", String.class, byte[].class);
  }

  @Test
  void representative_query_and_event_calls_still_delegate() {
    @SuppressWarnings("unchecked")
    QueryResultsIterator<KeyValue> stateIterator = mock(QueryResultsIterator.class);
    @SuppressWarnings("unchecked")
    QueryResultsIterator<KeyModification> historyIterator = mock(QueryResultsIterator.class);
    @SuppressWarnings("unchecked")
    QueryResultsIteratorWithMetadata<KeyValue> pagedIterator =
        mock(QueryResultsIteratorWithMetadata.class);
    Response response = new Response(200, "ok", "done".getBytes(StandardCharsets.UTF_8));

    when(fabricStub.getStateByRange("a", "z")).thenReturn(stateIterator);
    when(fabricStub.getHistoryForKey("asset-123")).thenReturn(historyIterator);
    when(fabricStub.getQueryResultWithPagination("{}", 10, "bookmark")).thenReturn(pagedIterator);
    when(fabricStub.invokeChaincode("othercc", List.of(), null)).thenReturn(response);

    assertSame(stateIterator, middleware.getStateByRange("a", "z"));
    assertSame(historyIterator, middleware.getHistoryForKey("asset-123"));
    assertSame(pagedIterator, middleware.getQueryResultWithPagination("{}", 10, "bookmark"));
    assertSame(response, middleware.invokeChaincode("othercc", List.of(), null));
    middleware.setEvent("eventName", "payload".getBytes(StandardCharsets.UTF_8));

    verify(fabricStub).getStateByRange("a", "z");
    verify(fabricStub).getHistoryForKey("asset-123");
    verify(fabricStub).getQueryResultWithPagination("{}", 10, "bookmark");
    verify(fabricStub).invokeChaincode("othercc", List.of(), null);
    verify(fabricStub).setEvent("eventName", "payload".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void no_calls_are_silently_swallowed_for_core_state_methods() {
    byte[] value = "data".getBytes(StandardCharsets.UTF_8);
    when(fabricStub.getState("k")).thenReturn(value);

    middleware.getState("k");
    middleware.putState("k", value);
    middleware.delState("k");

    verify(fabricStub).getState("k");
    verify(fabricStub).putState("k", value);
    verify(fabricStub).delState("k");
    verifyNoMoreInteractions(fabricStub);
  }

  private void assertOverridden(final String methodName, final Class<?>... parameterTypes)
      throws Exception {
    Method method = LoggingStubMiddleware.class.getMethod(methodName, parameterTypes);
    assertEquals(LoggingStubMiddleware.class, method.getDeclaringClass(), methodName);
  }

  private Object getField(final Object target, final String fieldName) throws Exception {
    Field field = LoggingStubMiddleware.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  @MiddlewareInfo({LoggingStubMiddleware.class})
  @LoggingConfig(
      defaultKeyMode = KeyLogMode.FULL_KEY,
      defaultValueMode = ValueLogMode.VALUE_UTF8,
      collections = {
        @CollectionLogConfig(
            name = "privateMedical",
            keyMode = KeyLogMode.KEY_HASH,
            valueMode = ValueLogMode.VALUE_METADATA)
      })
  private static class ConfiguredContract implements HypernateContract {}
}
