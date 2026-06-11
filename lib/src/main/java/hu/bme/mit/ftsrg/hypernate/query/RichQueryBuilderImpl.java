/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link RichQueryBuilder} implementation backed by {@link ChaincodeStub#getQueryResult}.
 *
 * @param <T> the entity type returned by the query
 */
public final class RichQueryBuilderImpl<T> implements RichQueryBuilder<T> {

  static final String STALE_READ_WARNING_MESSAGE =
      "Rich query executed with uncommitted writes in cache; results may not reflect pending "
          + "transaction changes.";

  private static final Logger logger = LoggerFactory.getLogger(RichQueryBuilderImpl.class);

  private final ChaincodeStub stub;
  private final Class<T> entityClass;
  private final Set<String> referencedFields = new LinkedHashSet<>();
  private final List<SelectorAssembler.SortClause> sortClauses = new ArrayList<>();

  private SelectorNode selector;
  private Integer limit;
  private Integer skip;
  private boolean staleReadsAcknowledged = false;

  /**
   * Create a rich-query builder for the given entity type.
   *
   * @param stub the chaincode stub used to execute the query
   * @param entityClass the entity type returned by the query
   */
  public RichQueryBuilderImpl(final ChaincodeStub stub, final Class<T> entityClass) {
    this.stub = Objects.requireNonNull(stub, "stub must not be null");
    this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
  }

  /** {@inheritDoc} */
  @Override
  public FieldPredicate<T> where(final String field) {
    return predicateFor(field, selector == null ? null : CouchDbOperators.AND);
  }

  /** {@inheritDoc} */
  @Override
  public FieldPredicate<T> and(final String field) {
    return predicateFor(field, CouchDbOperators.AND);
  }

  /** {@inheritDoc} */
  @Override
  public FieldPredicate<T> or(final String field) {
    return predicateFor(field, CouchDbOperators.OR);
  }

  /** {@inheritDoc} */
  @Override
  public RichQueryBuilder<T> sortBy(final String field, final SortOrder order) {
    referencedFields.add(requireFieldName(field));
    sortClauses.add(
        new SelectorAssembler.SortClause(
            requireFieldName(field), Objects.requireNonNull(order, "order must not be null")));
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public RichQueryBuilder<T> limit(final int n) {
    if (n < 0) {
      throw new IllegalArgumentException("limit must not be negative");
    }

    this.limit = n;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public RichQueryBuilder<T> skip(final int n) {
    if (n < 0) {
      throw new IllegalArgumentException("skip must not be negative");
    }

    this.skip = n;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public RichQueryBuilder<T> acknowledgeStaleReads() {
    this.staleReadsAcknowledged = true;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public List<T> execute() throws HypernateException {
    validateReferencedFields();

    if (!staleReadsAcknowledged && hasDirtyWriteBackCache(stub)) {
      logger.warn(STALE_READ_WARNING_MESSAGE);
    }

    final String selectorJson = SelectorAssembler.assemble(selector, sortClauses, limit, skip);
    final QueryResultsIterator<KeyValue> results = stub.getQueryResult(selectorJson);
    try {
      return deserialize(results);
    } finally {
      close(results);
    }
  }

  private FieldPredicate<T> predicateFor(final String field, final String logicalOperator) {
    final String normalizedField = requireFieldName(field);
    referencedFields.add(normalizedField);

    return new FieldPredicate<>() {
      @Override
      public RichQueryBuilder<T> is(final Object value) {
        return addComparison(normalizedField, CouchDbOperators.EQ, value, logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> isNot(final Object value) {
        return addComparison(normalizedField, CouchDbOperators.NE, value, logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> greaterThan(final Object value) {
        return addComparison(normalizedField, CouchDbOperators.GT, value, logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> greaterThanOrEqualTo(final Object value) {
        return addComparison(normalizedField, CouchDbOperators.GTE, value, logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> lessThan(final Object value) {
        return addComparison(normalizedField, CouchDbOperators.LT, value, logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> lessThanOrEqualTo(final Object value) {
        return addComparison(normalizedField, CouchDbOperators.LTE, value, logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> in(final Object... values) {
        return addComparison(
            normalizedField,
            CouchDbOperators.IN,
            Arrays.asList(values == null ? new Object[0] : values.clone()),
            logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> notIn(final Object... values) {
        return addComparison(
            normalizedField,
            CouchDbOperators.NIN,
            Arrays.asList(values == null ? new Object[0] : values.clone()),
            logicalOperator);
      }

      @Override
      public RichQueryBuilder<T> exists(final boolean exists) {
        return addComparison(normalizedField, CouchDbOperators.EXISTS, exists, logicalOperator);
      }
    };
  }

  private RichQueryBuilder<T> addComparison(
      final String field, final String operator, final Object value, final String logicalOperator) {
    final SelectorNode comparison = new ComparisonNode(field, operator, value);
    if (selector == null) {
      selector = comparison;
      return this;
    }

    final String effectiveOperator =
        logicalOperator == null ? CouchDbOperators.AND : logicalOperator;
    selector = appendSelector(selector, effectiveOperator, comparison);
    return this;
  }

  private SelectorNode appendSelector(
      final SelectorNode currentSelector,
      final String logicalOperator,
      final SelectorNode appendedSelector) {
    if (currentSelector instanceof LogicalNode logicalNode
        && logicalOperator.equals(logicalNode.operator())) {
      final List<SelectorNode> children = new ArrayList<>(logicalNode.children());
      children.add(appendedSelector);
      return new LogicalNode(logicalOperator, children);
    }

    return new LogicalNode(logicalOperator, List.of(currentSelector, appendedSelector));
  }

  private List<T> deserialize(final QueryResultsIterator<KeyValue> results) {
    final List<T> entities = new ArrayList<>();
    for (final KeyValue keyValue : results) {
      entities.add(
          JSON.deserialize(new String(keyValue.getValue(), StandardCharsets.UTF_8), entityClass));
    }

    return new QueryResult<>(entities).asList();
  }

  private void validateReferencedFields() {
    for (final String field : referencedFields) {
      if (findField(field) == null) {
        throw new IllegalArgumentException(
            "Unknown field '" + field + "' for entity " + entityClass.getName());
      }
    }
  }

  private Field findField(final String name) {
    Class<?> current = entityClass;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        current = current.getSuperclass();
      }
    }

    return null;
  }

  private boolean hasDirtyWriteBackCache(final ChaincodeStub currentStub) {
    ChaincodeStub current = currentStub;
    try {
      while (current != null) {
        if (current.getClass().getSimpleName().equals("WriteBackCachedStubMiddleware")) {
          final Field cacheField = current.getClass().getDeclaredField("cache");
          cacheField.setAccessible(true);
          @SuppressWarnings("unchecked")
          final Map<String, ?> cache = (Map<String, ?>) cacheField.get(current);
          for (final Object item : cache.values()) {
            final Field dirtyField = item.getClass().getDeclaredField("dirty");
            dirtyField.setAccessible(true);
            if ((boolean) dirtyField.get(item)) {
              return true;
            }
          }
        }

        if (!current.getClass().getSuperclass().getSimpleName().equals("StubMiddleware")) {
          return false;
        }

        final Field nextField = current.getClass().getSuperclass().getDeclaredField("nextStub");
        nextField.setAccessible(true);
        current = (ChaincodeStub) nextField.get(current);
      }
    } catch (ReflectiveOperationException | SecurityException e) {
      return false;
    }

    return false;
  }

  private void close(final QueryResultsIterator<KeyValue> results) {
    try {
      results.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to close rich query results", e);
    }
  }

  private String requireFieldName(final String field) {
    final String normalizedField = Objects.requireNonNull(field, "field must not be null").trim();
    if (normalizedField.isEmpty()) {
      throw new IllegalArgumentException("field must not be blank");
    }

    return normalizedField;
  }
}
