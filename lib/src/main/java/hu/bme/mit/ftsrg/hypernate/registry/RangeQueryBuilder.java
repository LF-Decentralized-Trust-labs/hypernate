/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.registry.query.InvalidRangeQueryException;
import java.util.List;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;

public class RangeQueryBuilder<T> {
  private final Registry registry;
  private final Class<T> clazz;
  private Object[] prefixParts;
  private boolean isFullScan = false;
  private boolean isPaginated = false;
  private int pageSize;
  private String bookmark;

  public RangeQueryBuilder(Registry registry, Class<T> clazz) {
    this.registry = registry;
    this.clazz = clazz;
  }

  public RangeQueryBuilder<T> withKeyPrefix(Object... prefixParts) {
    validatePrimaryKeyConfiguration();

    int expectedCount = Registry.EntityUtil.getPrimaryKeyCount(clazz);
    if (prefixParts.length > expectedCount) {
      throw new InvalidRangeQueryException(
          String.format(
              "Too many prefix parts for %s: provided %d, maximum is %d.",
              clazz.getName(), prefixParts.length, expectedCount));
    }

    for (int i = 0; i < prefixParts.length; i++) {
      if (prefixParts[i] == null) {
        throw new InvalidRangeQueryException(
            String.format(
                "Null prefix component at index %d for %s. Null composite key parts produce silently incorrect range scans.",
                i, clazz.getName()));
      }
    }

    this.prefixParts = prefixParts;
    this.isFullScan = false;
    return this;
  }

  public RangeQueryBuilder<T> fullScan() {
    validatePrimaryKeyConfiguration();
    this.isFullScan = true;
    this.prefixParts = new Object[0];
    return this;
  }

  public RangeQueryBuilder<T> paginated(int pageSize, String bookmark) {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be > 0");
    }
    this.isPaginated = true;
    this.pageSize = pageSize;
    this.bookmark = (bookmark == null) ? "" : bookmark;
    return this;
  }

  public List<T> execute() {
    if (!isFullScan && prefixParts == null) {
      throw new InvalidRangeQueryException(
          "execute() called without withKeyPrefix() or fullScan(). Configure the query before executing.");
    }

    org.hyperledger.fabric.shim.ledger.CompositeKey compositeKey = buildPartialKey();

    if (isPaginated) {
      try (QueryResultsIteratorWithMetadata<org.hyperledger.fabric.shim.ledger.KeyValue> iter =
          registry
              .getStub()
              .getStateByPartialCompositeKeyWithPagination(compositeKey, pageSize, bookmark)) {
        return QueryResultCollector.collect(iter, clazz);
      } catch (Exception e) {
        throw new RuntimeException("Failed to close iterator or collect results", e);
      }
    } else {
      try (org.hyperledger.fabric.shim.ledger.QueryResultsIterator<
              org.hyperledger.fabric.shim.ledger.KeyValue>
          iter = registry.getStub().getStateByPartialCompositeKey(compositeKey)) {
        return QueryResultCollector.collect(iter, clazz);
      } catch (Exception e) {
        throw new RuntimeException("Failed to close iterator or collect results", e);
      }
    }
  }

  public PagedResult<T> executePaged() {
    if (!isPaginated) {
      throw new IllegalStateException(
          "executePaged() requires .paginated() to be configured first.");
    }
    if (!isFullScan && prefixParts == null) {
      throw new InvalidRangeQueryException(
          "executePaged() called without withKeyPrefix() or fullScan().");
    }

    org.hyperledger.fabric.shim.ledger.CompositeKey compositeKey = buildPartialKey();
    try (QueryResultsIteratorWithMetadata<org.hyperledger.fabric.shim.ledger.KeyValue> iter =
        registry
            .getStub()
            .getStateByPartialCompositeKeyWithPagination(compositeKey, pageSize, bookmark)) {
      String nextBookmark = iter.getMetadata().getBookmark();
      boolean hasMore = !nextBookmark.isEmpty() || iter.getMetadata().getFetchedRecordsCount() > 0;
      List<T> results = QueryResultCollector.collect(iter, clazz);
      return new PagedResult<>(results, nextBookmark, hasMore);
    } catch (Exception e) {
      throw new RuntimeException("Failed to close iterator or collect results", e);
    }
  }

  private void validatePrimaryKeyConfiguration() {
    if (Registry.EntityUtil.getPrimaryKeyCount(clazz) == 0) {
      throw new InvalidRangeQueryException(
          String.format(
              "Cannot range-query %s: no @PrimaryKey annotation found. Range queries require at least one composite key field.",
              clazz.getName()));
    }
  }

  private org.hyperledger.fabric.shim.ledger.CompositeKey buildPartialKey() {
    String type = Registry.EntityUtil.getType(clazz);
    String[] mappedParts = Registry.EntityUtil.mapKeyPartsToString(clazz, prefixParts);
    return registry.getStub().createCompositeKey(type, mappedParts);
  }
}
