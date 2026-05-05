/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddleware;
import hu.bme.mit.ftsrg.hypernate.middleware.WriteBackCachedStubMiddleware;
import hu.bme.mit.ftsrg.hypernate.registry.query.UncommittedStateException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;

public class RichQueryBuilder<T> {

  private final Registry registry;
  private final Class<T> clazz;
  private final SelectorBuilder selectorBuilder = new SelectorBuilder();
  private final List<Map<String, String>> sort = new ArrayList<>();
  private String bookmark;
  private List<String> useIndex;
  private Integer limit;
  private Integer skip;

  public RichQueryBuilder(Registry registry, Class<T> clazz) {
    this.registry = registry;
    this.clazz = clazz;
    this.selectorBuilder.addCondition("docType", "$eq", Registry.EntityUtil.getType(clazz));
  }

  public ConditionBuilder where(String field) {
    return new ConditionBuilder(field);
  }

  public ConditionBuilder and(String field) {
    return new ConditionBuilder(field);
  }

  public RichQueryBuilder<T> or(RichQueryBuilder<T>... subQueries) {
    List<Map<String, Object>> subSelectors = new ArrayList<>();
    for (RichQueryBuilder<T> sq : subQueries) {
      subSelectors.add(sq.selectorBuilder.build());
    }
    selectorBuilder.addOr(subSelectors);
    return this;
  }

  /**
   * Add a sort condition. Note: CouchDB requires an index to exist on the sorted fields.
   * If an index does not exist, Fabric CouchDB runtime will return an error during execution.
   */
  public RichQueryBuilder<T> sortBy(String field, SortOrder order) {
    this.sort.add(Map.of(field, order == SortOrder.DESC ? "desc" : "asc"));
    return this;
  }

  public RichQueryBuilder<T> useIndex(String designDoc, String indexName) {
    this.useIndex = Arrays.asList(designDoc, indexName);
    return this;
  }

  public RichQueryBuilder<T> bookmark(String token) {
    this.bookmark = token;
    return this;
  }

  public RichQueryBuilder<T> limit(int n) {
    this.limit = n;
    return this;
  }

  public RichQueryBuilder<T> skip(int n) {
    this.skip = n;
    return this;
  }

  /**
   * Execute the rich query.
   * Note: Calling execute() with no conditions will result in a full-collection scan 
   * for the given entity type, matching all documents of this type.
   */
  public List<T> execute() throws UncommittedStateException {
    checkUncommittedState();
    String queryString = buildQueryString();
    try (org.hyperledger.fabric.shim.ledger.QueryResultsIterator<org.hyperledger.fabric.shim.ledger.KeyValue> iter = registry.getStub().getQueryResult(queryString)) {
      return QueryResultCollector.collect(iter, clazz);
    } catch (Exception e) {
      throw new RuntimeException("Failed to close iterator or collect results", e);
    }
  }

  public PagedResult<T> executePaged() throws UncommittedStateException {
    checkUncommittedState();
    String queryString = buildQueryString();
    int pageSize = (limit != null) ? limit : 25;
    String bm = (bookmark != null) ? bookmark : "";
    
    try (QueryResultsIteratorWithMetadata<org.hyperledger.fabric.shim.ledger.KeyValue> iter = 
        registry.getStub().getQueryResultWithPagination(queryString, pageSize, bm)) {
      String nextBookmark = iter.getMetadata().getBookmark();
      boolean hasMore = !nextBookmark.isEmpty() || iter.getMetadata().getFetchedRecordsCount() > 0;
      List<T> results = QueryResultCollector.collect(iter, clazz);
      return new PagedResult<>(results, nextBookmark, hasMore);
    } catch (Exception e) {
      throw new RuntimeException("Failed to close iterator or collect results", e);
    }
  }

  private String buildQueryString() {
    Map<String, Object> query = new HashMap<>();
    query.put("selector", selectorBuilder.build());
    if (!sort.isEmpty()) query.put("sort", sort);
    if (useIndex != null) query.put("use_index", useIndex);
    if (bookmark != null) query.put("bookmark", bookmark);
    if (limit != null) query.put("limit", limit);
    if (skip != null) query.put("skip", skip);

    try {
      return JSON.serialize(query);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize CouchDB query", e);
    }
  }

  private void checkUncommittedState() throws UncommittedStateException {
    ChaincodeStub current = registry.getStub();
    while (current instanceof StubMiddleware sm) {
      if (current instanceof WriteBackCachedStubMiddleware wbc) {
        if (wbc.hasUncommittedState()) {
          throw new UncommittedStateException("Rich query cannot proceed: uncommitted writes exist in the middleware cache. "
              + "CouchDB reads committed ledger state only — buffered writes in WriteBackCachedStubMiddleware are invisible to getQueryResult(). "
              + "Flush pending writes via ctx.notify(new TransactionEnd()) before querying, or restructure your transaction to query before writing.");
        }
      }
      current = sm.getNextStub();
    }
  }

  public class ConditionBuilder {
    private final String field;

    private ConditionBuilder(String field) {
      this.field = field;
    }

    public RichQueryBuilder<T> is(Object value) {
      selectorBuilder.addCondition(field, "$eq", value);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> isNot(Object value) {
      selectorBuilder.addCondition(field, "$ne", value);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> greaterThan(Object value) {
      selectorBuilder.addCondition(field, "$gt", value);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> greaterThanOrEq(Object value) {
      selectorBuilder.addCondition(field, "$gte", value);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> lessThan(Object value) {
      selectorBuilder.addCondition(field, "$lt", value);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> lessThanOrEq(Object value) {
      selectorBuilder.addCondition(field, "$lte", value);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> in(Object... values) {
      selectorBuilder.addCondition(field, "$in", Arrays.asList(values));
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> notIn(Object... values) {
      selectorBuilder.addCondition(field, "$nin", Arrays.asList(values));
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> exists(boolean present) {
      selectorBuilder.addCondition(field, "$exists", present);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> regex(String pattern) {
      selectorBuilder.addCondition(field, "$regex", pattern);
      return RichQueryBuilder.this;
    }

    public RichQueryBuilder<T> between(Object low, Object high) {
      selectorBuilder.addCondition(field, "$gte", low);
      selectorBuilder.addCondition(field, "$lte", high);
      return RichQueryBuilder.this;
    }
  }
}
