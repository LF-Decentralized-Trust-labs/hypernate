/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.registry.MissingPrimaryKeysException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

/**
 * Default {@link RangeQueryBuilder} implementation backed by partial composite-key scans.
 *
 * @param <T> the entity type returned by the query
 */
public final class RangeQueryBuilderImpl<T> implements RangeQueryBuilder<T> {

  private final ChaincodeStub stub;
  private final Class<T> entityClass;
  private final List<KeyPart> keyParts = new ArrayList<>();

  /**
   * Create a range-query builder for the given entity type.
   *
   * @param stub the chaincode stub used to execute the query
   * @param entityClass the entity type returned by the query
   */
  public RangeQueryBuilderImpl(final ChaincodeStub stub, final Class<T> entityClass) {
    this.stub = Objects.requireNonNull(stub, "stub must not be null");
    this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
  }

  /** {@inheritDoc} */
  @Override
  public RangeKeyPredicate<T> whereKey(final String primaryKeyField) {
    final String normalizedField =
        Objects.requireNonNull(primaryKeyField, "primaryKeyField must not be null").trim();
    if (normalizedField.isEmpty()) {
      throw new IllegalArgumentException("primaryKeyField must not be blank");
    }

    return value -> {
      keyParts.add(new KeyPart(normalizedField, value));
      return this;
    };
  }

  /** {@inheritDoc} */
  @Override
  public List<T> execute() throws HypernateException {
    if (keyParts.isEmpty()) {
      throw new IllegalArgumentException(
          "rangeQuery("
              + entityClass.getSimpleName()
              + ").execute() was called with no whereKey() constraints. This would perform a "
              + "full-type scan equivalent to readAll(). Use registry.readAll("
              + entityClass.getSimpleName()
              + ".class) explicitly if that is your intent.");
    }

    final AttributeInfo[] primaryKey = getPrimaryKeyAttributes();
    validateLeftPrefix(primaryKey);

    final String[] encodedKeyParts = new String[keyParts.size()];
    for (int i = 0; i < keyParts.size(); i++) {
      encodedKeyParts[i] = applyAttributeMapper(primaryKey[i], keyParts.get(i).value());
    }

    final QueryResultsIterator<KeyValue> results =
        stub.getStateByPartialCompositeKey(getEntityType(), encodedKeyParts);
    try {
      return deserialize(results);
    } finally {
      close(results);
    }
  }

  private List<T> deserialize(final QueryResultsIterator<KeyValue> results) {
    final List<T> entities = new ArrayList<>();
    for (final KeyValue keyValue : results) {
      entities.add(
          JSON.deserialize(new String(keyValue.getValue(), StandardCharsets.UTF_8), entityClass));
    }

    return new QueryResult<>(entities).asList();
  }

  private AttributeInfo[] getPrimaryKeyAttributes() {
    final PrimaryKey primaryKey = entityClass.getAnnotation(PrimaryKey.class);
    if (primaryKey == null) {
      throw new MissingPrimaryKeysException(
          String.format("%s does not have a primary key annotation", entityClass));
    }

    return primaryKey.value();
  }

  private void validateLeftPrefix(final AttributeInfo[] primaryKeyAttributes) {
    if (keyParts.size() > primaryKeyAttributes.length) {
      throw new IllegalArgumentException(
          "Too many key parts provided for entity " + entityClass.getName());
    }

    for (int i = 0; i < keyParts.size(); i++) {
      final String expectedField = primaryKeyAttributes[i].name();
      final String providedField = keyParts.get(i).field();
      if (!expectedField.equals(providedField)) {
        throw new IllegalArgumentException(
            "Range query key parts must follow the @PrimaryKey left prefix order for "
                + entityClass.getName()
                + ": expected '"
                + expectedField
                + "' at position "
                + i
                + " but got '"
                + providedField
                + "'");
      }
    }
  }

  private String applyAttributeMapper(final AttributeInfo attrInfo, final Object keyPart) {
    final Class<? extends AttributeMapper> mapperClass = attrInfo.mapper();
    final Constructor<? extends AttributeMapper> constructor;
    try {
      constructor = mapperClass.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Could not find no-arg constructor for mapper " + mapperClass, e);
    }

    final AttributeMapper mapper;
    try {
      mapper = constructor.newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException("Failed to instantiate mapper " + mapperClass, e);
    }

    return mapper.apply(keyPart);
  }

  private String getEntityType() {
    return entityClass.getName().toUpperCase(Locale.ROOT);
  }

  private void close(final QueryResultsIterator<KeyValue> results) {
    try {
      results.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to close range query results", e);
    }
  }

  private record KeyPart(String field, Object value) {}
}
