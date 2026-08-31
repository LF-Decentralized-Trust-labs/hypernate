/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Stub middleware that logs ledger-facing {@link ChaincodeStub} operations using configurable key
 * and value redaction.
 *
 * @see StubMiddleware
 */
public class LoggingStubMiddleware extends StubMiddleware {

  private static final String PUBLIC_STATE_COLLECTION = "";

  private static final int TRUNCATED_VALUE_CHAR_LIMIT = 256;

  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private static final LogConfig DEFAULT_PUBLIC_LOG_CONFIG =
      new LogConfig(KeyLogMode.KEY_PREFIX, ValueLogMode.KEYS_ONLY);

  private static final LogConfig DEFAULT_PRIVATE_LOG_CONFIG =
      new LogConfig(KeyLogMode.KEY_HASH, ValueLogMode.KEYS_ONLY);

  private final Logger logger;

  private final Level logLevel;

  private final LogConfig publicDefaultConfig;

  private final LogConfig privateDefaultConfig;

  private final Map<String, LogConfig> collectionConfigs;

  public LoggingStubMiddleware() {
    this(LoggerFactory.getLogger(LoggingStubMiddleware.class));
  }

  public LoggingStubMiddleware(final Logger logger) {
    this(logger, Level.DEBUG);
  }

  public LoggingStubMiddleware(final Logger logger, final Level logLevel) {
    this(logger, logLevel, DEFAULT_PUBLIC_LOG_CONFIG, DEFAULT_PRIVATE_LOG_CONFIG, Map.of());
  }

  public LoggingStubMiddleware(
      final Logger logger,
      final Level logLevel,
      final LogConfig defaultConfig,
      final Map<String, LogConfig> collectionConfigs) {
    this(logger, logLevel, defaultConfig, defaultConfig, collectionConfigs);
  }

  public static LoggingStubMiddleware fromAnnotation(final LoggingConfig loggingConfig) {
    return fromAnnotation(
        LoggerFactory.getLogger(LoggingStubMiddleware.class), Level.DEBUG, loggingConfig);
  }

  static LoggingStubMiddleware fromAnnotation(
      final Logger logger, final Level logLevel, final LoggingConfig loggingConfig) {
    LogConfig defaultConfig =
        new LogConfig(loggingConfig.defaultKeyMode(), loggingConfig.defaultValueMode());
    Map<String, LogConfig> collectionConfigs =
        Arrays.stream(loggingConfig.collections())
            .collect(
                Collectors.toUnmodifiableMap(
                    CollectionLogConfig::name,
                    config -> new LogConfig(config.keyMode(), config.valueMode())));
    return new LoggingStubMiddleware(
        logger, logLevel, defaultConfig, defaultConfig, collectionConfigs);
  }

  private LoggingStubMiddleware(
      final Logger logger,
      final Level logLevel,
      final LogConfig publicDefaultConfig,
      final LogConfig privateDefaultConfig,
      final Map<String, LogConfig> collectionConfigs) {
    this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    this.logLevel = Objects.requireNonNull(logLevel, "logLevel cannot be null");
    this.publicDefaultConfig =
        Objects.requireNonNull(publicDefaultConfig, "publicDefaultConfig cannot be null");
    this.privateDefaultConfig =
        Objects.requireNonNull(privateDefaultConfig, "privateDefaultConfig cannot be null");
    this.collectionConfigs = collectionConfigs == null ? Map.of() : Map.copyOf(collectionConfigs);
  }

  @Override
  public byte[] getState(final String key) {
    LogConfig config = getPublicConfig();
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("getState", keyDetail);
    byte[] value = this.nextStub.getState(key);
    logOperation(
        "getState result", keyDetail, formatBinaryValueDetail("value", value, config.valueMode()));
    return value;
  }

  @Override
  public String getStringState(final String key) {
    LogConfig config = getPublicConfig();
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("getStringState", keyDetail);
    String value = this.nextStub.getStringState(key);
    logOperation(
        "getStringState result",
        keyDetail,
        formatTextValueDetail("value", value, config.valueMode()));
    return value;
  }

  @Override
  public void putState(final String key, final byte[] value) {
    LogConfig config = getPublicConfig();
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation(
        "putState", keyDetail, formatBinaryValueDetail("value", value, config.valueMode()));
    this.nextStub.putState(key, value);
    logOperation("putState complete", keyDetail);
  }

  @Override
  public void putStringState(final String key, final String value) {
    LogConfig config = getPublicConfig();
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation(
        "putStringState", keyDetail, formatTextValueDetail("value", value, config.valueMode()));
    this.nextStub.putStringState(key, value);
    logOperation("putStringState complete", keyDetail);
  }

  @Override
  public void setStateValidationParameter(final String key, final byte[] value) {
    LogConfig config = getPublicConfig();
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("setStateValidationParameter", keyDetail);
    this.nextStub.setStateValidationParameter(key, value);
    logOperation("setStateValidationParameter complete", keyDetail);
  }

  @Override
  public void delState(final String key) {
    LogConfig config = getPublicConfig();
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("delState", keyDetail);
    this.nextStub.delState(key);
    logOperation("delState complete", keyDetail);
  }

  @Override
  public QueryResultsIterator<KeyValue> getStateByRange(
      final String startKey, final String endKey) {
    LogConfig config = getPublicConfig();
    logOperation(
        "getStateByRange",
        formatKeyDetail("startKey", startKey, config.keyMode()),
        formatKeyDetail("endKey", endKey, config.keyMode()));
    return this.nextStub.getStateByRange(startKey, endKey);
  }

  @Override
  public QueryResultsIteratorWithMetadata<KeyValue> getStateByRangeWithPagination(
      final String startKey, final String endKey, final int pageSize, final String bookmark) {
    LogConfig config = getPublicConfig();
    logOperation(
        "getStateByRangeWithPagination",
        formatKeyDetail("startKey", startKey, config.keyMode()),
        formatKeyDetail("endKey", endKey, config.keyMode()),
        "pageSize=" + pageSize,
        formatOpaqueTextDetail("bookmark", bookmark, config.keyMode()));
    return this.nextStub.getStateByRangeWithPagination(startKey, endKey, pageSize, bookmark);
  }

  @Override
  public QueryResultsIterator<KeyValue> getStateByPartialCompositeKey(final String compositeKey) {
    LogConfig config = getPublicConfig();
    logOperation(
        "getStateByPartialCompositeKey",
        formatCompositeKeyStringDetail("compositeKey", compositeKey, config.keyMode()));
    return this.nextStub.getStateByPartialCompositeKey(compositeKey);
  }

  @Override
  public QueryResultsIterator<KeyValue> getStateByPartialCompositeKey(
      final String objectType, final String... attributes) {
    LogConfig config = getPublicConfig();
    logOperation(
        "getStateByPartialCompositeKey",
        formatCompositeKeyPartsDetail("compositeKey", objectType, attributes, config.keyMode()));
    return this.nextStub.getStateByPartialCompositeKey(objectType, attributes);
  }

  @Override
  public QueryResultsIterator<KeyValue> getStateByPartialCompositeKey(
      final CompositeKey compositeKey) {
    LogConfig config = getPublicConfig();
    logOperation(
        "getStateByPartialCompositeKey",
        formatCompositeKeyDetail("compositeKey", compositeKey, config.keyMode()));
    return this.nextStub.getStateByPartialCompositeKey(compositeKey);
  }

  @Override
  public QueryResultsIteratorWithMetadata<KeyValue> getStateByPartialCompositeKeyWithPagination(
      final CompositeKey compositeKey, final int pageSize, final String bookmark) {
    LogConfig config = getPublicConfig();
    logOperation(
        "getStateByPartialCompositeKeyWithPagination",
        formatCompositeKeyDetail("compositeKey", compositeKey, config.keyMode()),
        "pageSize=" + pageSize,
        formatOpaqueTextDetail("bookmark", bookmark, config.keyMode()));
    return this.nextStub.getStateByPartialCompositeKeyWithPagination(
        compositeKey, pageSize, bookmark);
  }

  @Override
  public QueryResultsIterator<KeyValue> getQueryResult(final String query) {
    LogConfig config = getPublicConfig();
    logOperation("getQueryResult", formatOpaqueTextDetail("query", query, config.keyMode()));
    return this.nextStub.getQueryResult(query);
  }

  @Override
  public QueryResultsIteratorWithMetadata<KeyValue> getQueryResultWithPagination(
      final String query, final int pageSize, final String bookmark) {
    LogConfig config = getPublicConfig();
    logOperation(
        "getQueryResultWithPagination",
        formatOpaqueTextDetail("query", query, config.keyMode()),
        "pageSize=" + pageSize,
        formatOpaqueTextDetail("bookmark", bookmark, config.keyMode()));
    return this.nextStub.getQueryResultWithPagination(query, pageSize, bookmark);
  }

  @Override
  public QueryResultsIterator<KeyModification> getHistoryForKey(final String key) {
    LogConfig config = getPublicConfig();
    logOperation("getHistoryForKey", formatKeyDetail("key", key, config.keyMode()));
    return this.nextStub.getHistoryForKey(key);
  }

  @Override
  public byte[] getPrivateData(final String collection, final String key) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("getPrivateData", collectionDetail, keyDetail);
    byte[] value = this.nextStub.getPrivateData(collection, key);
    logOperation(
        "getPrivateData result",
        collectionDetail,
        keyDetail,
        formatBinaryValueDetail("value", value, config.valueMode()));
    return value;
  }

  @Override
  public void putPrivateData(final String collection, final String key, final byte[] value) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation(
        "putPrivateData",
        collectionDetail,
        keyDetail,
        formatBinaryValueDetail("value", value, config.valueMode()));
    this.nextStub.putPrivateData(collection, key, value);
    logOperation("putPrivateData complete", collectionDetail, keyDetail);
  }

  @Override
  public void putPrivateData(final String collection, final String key, final String value) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation(
        "putPrivateData",
        collectionDetail,
        keyDetail,
        formatTextValueDetail("value", value, config.valueMode()));
    this.nextStub.putPrivateData(collection, key, value);
    logOperation("putPrivateData complete", collectionDetail, keyDetail);
  }

  @Override
  public void setPrivateDataValidationParameter(
      final String collection, final String key, final byte[] value) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("setPrivateDataValidationParameter", collectionDetail, keyDetail);
    this.nextStub.setPrivateDataValidationParameter(collection, key, value);
    logOperation("setPrivateDataValidationParameter complete", collectionDetail, keyDetail);
  }

  @Override
  public void delPrivateData(final String collection, final String key) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("delPrivateData", collectionDetail, keyDetail);
    this.nextStub.delPrivateData(collection, key);
    logOperation("delPrivateData complete", collectionDetail, keyDetail);
  }

  @Override
  public void purgePrivateData(final String collection, final String key) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("purgePrivateData", collectionDetail, keyDetail);
    this.nextStub.purgePrivateData(collection, key);
    logOperation("purgePrivateData complete", collectionDetail, keyDetail);
  }

  @Override
  public byte[] getPrivateDataHash(final String collection, final String key) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("getPrivateDataHash", collectionDetail, keyDetail);
    byte[] value = this.nextStub.getPrivateDataHash(collection, key);
    logOperation(
        "getPrivateDataHash result",
        collectionDetail,
        keyDetail,
        formatBinaryValueDetail("hash", value, config.valueMode()));
    return value;
  }

  @Override
  public byte[] getPrivateDataValidationParameter(final String collection, final String key) {
    LogConfig config = getCollectionConfig(collection);
    String collectionDetail = formatCollectionDetail(collection);
    String keyDetail = formatKeyDetail("key", key, config.keyMode());
    logOperation("getPrivateDataValidationParameter", collectionDetail, keyDetail);
    byte[] value = this.nextStub.getPrivateDataValidationParameter(collection, key);
    logOperation(
        "getPrivateDataValidationParameter result",
        collectionDetail,
        keyDetail,
        formatBinaryValueDetail("endorsementPolicy", value, config.valueMode()));
    return value;
  }

  @Override
  public QueryResultsIterator<KeyValue> getPrivateDataByRange(
      final String collection, final String startKey, final String endKey) {
    LogConfig config = getCollectionConfig(collection);
    logOperation(
        "getPrivateDataByRange",
        formatCollectionDetail(collection),
        formatKeyDetail("startKey", startKey, config.keyMode()),
        formatKeyDetail("endKey", endKey, config.keyMode()));
    return this.nextStub.getPrivateDataByRange(collection, startKey, endKey);
  }

  @Override
  public QueryResultsIterator<KeyValue> getPrivateDataByPartialCompositeKey(
      final String collection, final String compositeKey) {
    LogConfig config = getCollectionConfig(collection);
    logOperation(
        "getPrivateDataByPartialCompositeKey",
        formatCollectionDetail(collection),
        formatCompositeKeyStringDetail("compositeKey", compositeKey, config.keyMode()));
    return this.nextStub.getPrivateDataByPartialCompositeKey(collection, compositeKey);
  }

  @Override
  public QueryResultsIterator<KeyValue> getPrivateDataByPartialCompositeKey(
      final String collection, final CompositeKey compositeKey) {
    LogConfig config = getCollectionConfig(collection);
    logOperation(
        "getPrivateDataByPartialCompositeKey",
        formatCollectionDetail(collection),
        formatCompositeKeyDetail("compositeKey", compositeKey, config.keyMode()));
    return this.nextStub.getPrivateDataByPartialCompositeKey(collection, compositeKey);
  }

  @Override
  public QueryResultsIterator<KeyValue> getPrivateDataByPartialCompositeKey(
      final String collection, final String objectType, final String... attributes) {
    LogConfig config = getCollectionConfig(collection);
    logOperation(
        "getPrivateDataByPartialCompositeKey",
        formatCollectionDetail(collection),
        formatCompositeKeyPartsDetail("compositeKey", objectType, attributes, config.keyMode()));
    return this.nextStub.getPrivateDataByPartialCompositeKey(collection, objectType, attributes);
  }

  @Override
  public QueryResultsIterator<KeyValue> getPrivateDataQueryResult(
      final String collection, final String query) {
    LogConfig config = getCollectionConfig(collection);
    logOperation(
        "getPrivateDataQueryResult",
        formatCollectionDetail(collection),
        formatOpaqueTextDetail("query", query, config.keyMode()));
    return this.nextStub.getPrivateDataQueryResult(collection, query);
  }

  @Override
  public Response invokeChaincode(
      final String chaincodeName, final List<byte[]> args, final String channel) {
    logOperation(
        "invokeChaincode",
        "chaincode='" + chaincodeName + "'",
        "channel='" + (channel == null || channel.isBlank() ? "<current>" : channel) + "'");
    Response response = this.nextStub.invokeChaincode(chaincodeName, args, channel);
    logOperation("invokeChaincode complete", "chaincode='" + chaincodeName + "'");
    return response;
  }

  @Override
  public void setEvent(final String name, final byte[] payload) {
    logOperation(
        "setEvent",
        "name='" + name + "'",
        "payloadBytes=" + (payload == null ? 0 : payload.length));
    this.nextStub.setEvent(name, payload);
    logOperation("setEvent complete", "name='" + name + "'");
  }

  private LogConfig getPublicConfig() {
    return collectionConfigs.getOrDefault(PUBLIC_STATE_COLLECTION, publicDefaultConfig);
  }

  private LogConfig getCollectionConfig(final String collection) {
    return collectionConfigs.getOrDefault(collection, privateDefaultConfig);
  }

  private void logOperation(final String operation, final String... details) {
    String detailText =
        Arrays.stream(details)
            .filter(Objects::nonNull)
            .filter(detail -> !detail.isBlank())
            .collect(Collectors.joining(", "));
    if (detailText.isBlank()) {
      log(operation);
    } else {
      log(operation + ": " + detailText);
    }
  }

  private String formatCollectionDetail(final String collection) {
    return "collection='" + collection + "'";
  }

  private String formatKeyDetail(final String label, final String key, final KeyLogMode mode) {
    return switch (mode) {
      case OPERATION_ONLY -> null;
      case KEY_HASH -> formatHashedTextDetail(label, key);
      case KEY_PREFIX -> formatPrefixDetail(label, key);
      case FULL_KEY -> label + "='" + key + "'";
    };
  }

  private String formatOpaqueTextDetail(
      final String label, final String text, final KeyLogMode mode) {
    return switch (mode) {
      case OPERATION_ONLY -> null;
      case KEY_HASH -> formatHashedTextDetail(label, text);
      case KEY_PREFIX -> label + "{length=" + utf8Length(text) + "}";
      case FULL_KEY -> label + "='" + text + "'";
    };
  }

  private String formatCompositeKeyStringDetail(
      final String label, final String compositeKey, final KeyLogMode mode) {
    if (compositeKey != null && compositeKey.startsWith(CompositeKey.NAMESPACE)) {
      try {
        return formatCompositeKeyDetail(label, CompositeKey.parseCompositeKey(compositeKey), mode);
      } catch (RuntimeException ignored) {
        // Fall back to raw string handling below.
      }
    }
    return formatKeyDetail(label, compositeKey, mode);
  }

  private String formatCompositeKeyDetail(
      final String label, final CompositeKey compositeKey, final KeyLogMode mode) {
    if (mode == KeyLogMode.OPERATION_ONLY) {
      return null;
    }

    List<String> attributes = compositeKey == null ? List.of() : compositeKey.getAttributes();
    String objectType = compositeKey == null ? null : compositeKey.getObjectType();
    return switch (mode) {
      case KEY_HASH -> formatHashedTextDetail(
          label, compositeKey == null ? null : compositeKey.toString());
      case KEY_PREFIX -> label
          + "{objectType='"
          + objectType
          + "', attributeCount="
          + attributes.size()
          + "}";
      case FULL_KEY -> label + "{objectType='" + objectType + "', attributes=" + attributes + "}";
      case OPERATION_ONLY -> null;
    };
  }

  private String formatCompositeKeyPartsDetail(
      final String label,
      final String objectType,
      final String[] attributes,
      final KeyLogMode mode) {
    if (mode == KeyLogMode.OPERATION_ONLY) {
      return null;
    }

    String[] nonNullAttributes = attributes == null ? new String[0] : attributes;
    return switch (mode) {
      case KEY_HASH -> formatHashedTextDetail(
          label, new CompositeKey(objectType, nonNullAttributes).toString());
      case KEY_PREFIX -> label
          + "{objectType='"
          + objectType
          + "', attributeCount="
          + nonNullAttributes.length
          + "}";
      case FULL_KEY -> label
          + "{objectType='"
          + objectType
          + "', attributes="
          + Arrays.toString(nonNullAttributes)
          + "}";
      case OPERATION_ONLY -> null;
    };
  }

  private String formatPrefixDetail(final String label, final String key) {
    if (key != null && key.startsWith(CompositeKey.NAMESPACE)) {
      try {
        CompositeKey compositeKey = CompositeKey.parseCompositeKey(key);
        return label
            + "{objectType='"
            + compositeKey.getObjectType()
            + "', attributeCount="
            + compositeKey.getAttributes().size()
            + "}";
      } catch (RuntimeException ignored) {
        // Fall back to opaque handling below.
      }
    }
    return label + "{kind=simple,length=" + utf8Length(key) + "}";
  }

  private String formatHashedTextDetail(final String label, final String text) {
    return label
        + "{bytes="
        + utf8Length(text)
        + ",sha256='"
        + sha256Hex(text == null ? null : text.getBytes(UTF_8))
        + "'}";
  }

  private String formatBinaryValueDetail(
      final String label, final byte[] value, final ValueLogMode mode) {
    return switch (mode) {
      case KEYS_ONLY -> null;
      case VALUE_METADATA -> label
          + "{bytes="
          + byteLength(value)
          + ",sha256='"
          + sha256Hex(value)
          + "'}";
      case VALUE_UTF8 -> label + "='" + decodeUtf8(value) + "'";
      case VALUE_UTF8_TRUNCATED -> label + "='" + truncate(decodeUtf8(value)) + "'";
    };
  }

  private String formatTextValueDetail(
      final String label, final String value, final ValueLogMode mode) {
    return switch (mode) {
      case KEYS_ONLY -> null;
      case VALUE_METADATA -> label
          + "{bytes="
          + utf8Length(value)
          + ",sha256='"
          + sha256Hex(value == null ? null : value.getBytes(UTF_8))
          + "'}";
      case VALUE_UTF8 -> label + "='" + value + "'";
      case VALUE_UTF8_TRUNCATED -> label + "='" + truncate(value) + "'";
    };
  }

  private String decodeUtf8(final byte[] value) {
    return value == null ? null : new String(value, UTF_8);
  }

  private int utf8Length(final String text) {
    return text == null ? 0 : text.getBytes(UTF_8).length;
  }

  private int byteLength(final byte[] value) {
    return value == null ? 0 : value.length;
  }

  private String truncate(final String value) {
    if (value == null || value.length() <= TRUNCATED_VALUE_CHAR_LIMIT) {
      return value;
    }
    return value.substring(0, TRUNCATED_VALUE_CHAR_LIMIT)
        + "...(truncated,chars="
        + value.length()
        + ")";
  }

  private String sha256Hex(final byte[] value) {
    if (value == null) {
      return "null";
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HEX_FORMAT.formatHex(digest.digest(value));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private void log(final String message) {
    logger.atLevel(logLevel).log(message);
  }
}
