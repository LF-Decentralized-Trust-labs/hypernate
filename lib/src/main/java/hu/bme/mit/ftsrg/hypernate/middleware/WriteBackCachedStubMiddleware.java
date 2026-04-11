/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import com.jcabi.aspects.Loggable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stub middleware that caches reads in a local state and manages write-backs. */
@Loggable(Loggable.DEBUG)
public final class WriteBackCachedStubMiddleware extends StubMiddleware {

  private final Logger logger = LoggerFactory.getLogger(WriteBackCachedStubMiddleware.class);

  public enum PreemptiveReadPolicy {
    ENABLED,
    DISABLED
  }

  public enum RestorationPolicy {
    ALLOW,
    DENY
  }

  public enum CachedItemState {
    STORED,
    MODIFIED,
    DELETED,
    PURGED
  }

  public record CacheKey(String collection, String key) implements Comparable<CacheKey> {
    @Override
    public int compareTo(CacheKey o) {
      int collCmp = this.collection.compareTo(o.collection);
      if (collCmp != 0) return collCmp;
      return this.key.compareTo(o.key);
    }
  }

  @Getter
  @Setter
  public static final class CachedItem {
    private byte[] value;
    private CachedItemState state;

    CachedItem(byte[] value, CachedItemState state) {
      this.value = value;
      this.state = state;
    }

    void update(byte[] value, CachedItemState state) {
      this.value = value;
      this.state = state;
    }
  }

  private final TreeMap<CacheKey, CachedItem> cache = new TreeMap<>();

  @Getter @Setter private PreemptiveReadPolicy preemptiveReadPolicy = PreemptiveReadPolicy.ENABLED;

  @Getter @Setter private RestorationPolicy restorationPolicy = RestorationPolicy.DENY;

  public WriteBackCachedStubMiddleware() {}

  public WriteBackCachedStubMiddleware(
      PreemptiveReadPolicy preemptivePolicy, RestorationPolicy restorationPolicy) {
    this.preemptiveReadPolicy = preemptivePolicy;
    this.restorationPolicy = restorationPolicy;
  }

  /**
   * Disposes the cache by flushing all MODIFIED, DELETED, and PURGED entries back to the ledger via
   * the next middleware or stub.
   */
  public void dispose() {
    for (Map.Entry<CacheKey, CachedItem> entry : cache.entrySet()) {
      CacheKey ck = entry.getKey();
      CachedItem item = entry.getValue();
      if (item == null) continue;

      boolean isWorld = ck.collection().isEmpty();

      switch (item.getState()) {
        case STORED:
          break;
        case MODIFIED:
          if (isWorld) {
            this.nextStub.putState(ck.key(), item.getValue());
          } else {
            this.nextStub.putPrivateData(ck.collection(), ck.key(), item.getValue());
          }
          break;
        case DELETED:
          if (isWorld) {
            this.nextStub.delState(ck.key());
          } else {
            this.nextStub.delPrivateData(ck.collection(), ck.key());
          }
          break;
        case PURGED:
          if (!isWorld) {
            this.nextStub.purgePrivateData(ck.collection(), ck.key());
          }
          break;
      }
    }
    cache.clear();
  }

  private byte[] doGet(CacheKey cacheKey, Function<CacheKey, byte[]> fetcher) {
    CachedItem item = cache.get(cacheKey);
    if (item == null) {
      byte[] fetched = fetcher.apply(cacheKey);
      item = new CachedItem(fetched, CachedItemState.STORED);
      cache.put(cacheKey, item);
    }

    if (item.getState() == CachedItemState.DELETED || item.getState() == CachedItemState.PURGED) {
      return null;
    }
    return item.getValue();
  }

  private void doPut(CacheKey cacheKey, byte[] value, Function<CacheKey, byte[]> fetcher) {
    CachedItem item = cache.get(cacheKey);

    if (item == null && preemptiveReadPolicy == PreemptiveReadPolicy.ENABLED) {
      doGet(cacheKey, fetcher);
      item = cache.get(cacheKey);
    }

    if (item == null) {
      cache.put(cacheKey, new CachedItem(value, CachedItemState.MODIFIED));
      return;
    }

    switch (item.getState()) {
      case STORED:
      case MODIFIED:
        item.update(value, CachedItemState.MODIFIED);
        break;
      case DELETED:
      case PURGED:
        if (restorationPolicy == RestorationPolicy.DENY) {
          throw new IllegalStateException(
              "Cannot replace a deleted or purged item when RestorationPolicy is DENY.");
        }
        item.update(value, CachedItemState.MODIFIED);
        break;
    }
  }

  private void doDelete(CacheKey cacheKey, Function<CacheKey, byte[]> fetcher) {
    CachedItem item = cache.get(cacheKey);

    if (item == null && preemptiveReadPolicy == PreemptiveReadPolicy.ENABLED) {
      doGet(cacheKey, fetcher);
      item = cache.get(cacheKey);
    }

    if (item == null) {
      cache.put(cacheKey, new CachedItem(null, CachedItemState.DELETED));
      return;
    }

    switch (item.getState()) {
      case STORED:
      case MODIFIED:
      case DELETED:
        item.update(null, CachedItemState.DELETED);
        break;
      case PURGED:
        throw new IllegalStateException(
            "Cannot logically delete an explicitly purged private data item");
    }
  }

  private void doPurge(CacheKey cacheKey, Function<CacheKey, byte[]> fetcher) {
    CachedItem item = cache.get(cacheKey);

    if (item == null && preemptiveReadPolicy == PreemptiveReadPolicy.ENABLED) {
      doGet(cacheKey, fetcher);
      item = cache.get(cacheKey);
    }

    if (item == null) {
      cache.put(cacheKey, new CachedItem(null, CachedItemState.PURGED));
      return;
    }

    switch (item.getState()) {
      case STORED:
      case MODIFIED:
      case DELETED:
      case PURGED:
        item.update(null, CachedItemState.PURGED);
        break;
    }
  }

  @Override
  public byte[] getState(String key) {
    return doGet(new CacheKey("", key), k -> this.nextStub.getState(k.key()));
  }

  @Override
  public void putState(String key, byte[] value) {
    doPut(new CacheKey("", key), value, k -> this.nextStub.getState(k.key()));
  }

  @Override
  public void delState(String key) {
    doDelete(new CacheKey("", key), k -> this.nextStub.getState(k.key()));
  }

  @Override
  public byte[] getPrivateData(String collection, String key) {
    return doGet(
        new CacheKey(collection, key), k -> this.nextStub.getPrivateData(k.collection(), k.key()));
  }

  @Override
  public void putPrivateData(String collection, String key, byte[] value) {
    doPut(
        new CacheKey(collection, key),
        value,
        k -> this.nextStub.getPrivateData(k.collection(), k.key()));
  }

  @Override
  public void delPrivateData(String collection, String key) {
    doDelete(
        new CacheKey(collection, key), k -> this.nextStub.getPrivateData(k.collection(), k.key()));
  }

  @Override
  public void purgePrivateData(String collection, String key) {
    doPurge(
        new CacheKey(collection, key), k -> this.nextStub.getPrivateData(k.collection(), k.key()));
  }

  @Override
  public QueryResultsIterator<KeyValue> getStateByRange(String startKey, String endKey) {
    return new CacheAwareIterator(
        this.nextStub.getStateByRange(startKey, endKey), "", startKey, endKey, true);
  }

  @Override
  public QueryResultsIteratorWithMetadata<KeyValue> getStateByRangeWithPagination(
      String startKey, String endKey, int pageSize, String bookmark) {
    return this.nextStub.getStateByRangeWithPagination(startKey, endKey, pageSize, bookmark);
  }

  @Override
  public QueryResultsIterator<KeyValue> getStateByPartialCompositeKey(String compositeKey) {
    return new CacheAwareIterator(
        this.nextStub.getStateByPartialCompositeKey(compositeKey),
        "",
        compositeKey,
        compositeKey + Character.MAX_VALUE,
        true);
  }

  @Override
  public QueryResultsIteratorWithMetadata<KeyValue> getStateByPartialCompositeKeyWithPagination(
      org.hyperledger.fabric.shim.ledger.CompositeKey compositeKey, int pageSize, String bookmark) {
    return this.nextStub.getStateByPartialCompositeKeyWithPagination(
        compositeKey, pageSize, bookmark);
  }

  @Override
  public QueryResultsIterator<KeyValue> getPrivateDataByRange(
      String collection, String startKey, String endKey) {
    return new CacheAwareIterator(
        this.nextStub.getPrivateDataByRange(collection, startKey, endKey),
        collection,
        startKey,
        endKey,
        true);
  }

  @Override
  public QueryResultsIterator<KeyValue> getPrivateDataByPartialCompositeKey(
      String collection, String compositeKey) {
    return new CacheAwareIterator(
        this.nextStub.getPrivateDataByPartialCompositeKey(collection, compositeKey),
        collection,
        compositeKey,
        compositeKey + Character.MAX_VALUE,
        true);
  }

  private class CacheAwareIterator implements QueryResultsIterator<KeyValue> {

    private final QueryResultsIterator<KeyValue> ledgerIterator;
    protected final Iterator<KeyValue> mergedIterator;

    CacheAwareIterator(
        QueryResultsIterator<KeyValue> ledgerIterator,
        String collection,
        String startKey,
        String endKey,
        boolean allowInjection) {

      this.ledgerIterator = ledgerIterator;

      CacheKey start = new CacheKey(collection, startKey);
      CacheKey end = new CacheKey(collection, endKey);

      Map<CacheKey, CachedItem> subCache;
      if (endKey == null || endKey.isEmpty()) {
        subCache = cache.tailMap(start);
      } else {
        subCache = cache.subMap(start, end);
      }

      TreeMap<String, byte[]> completeSet = new TreeMap<>();

      if (this.ledgerIterator != null) {
        for (KeyValue kv : this.ledgerIterator) {
          completeSet.put(kv.getKey(), kv.getValue());
        }
      }

      for (Map.Entry<CacheKey, CachedItem> entry : subCache.entrySet()) {
        if (!entry.getKey().collection().equals(collection)) continue;

        CachedItem item = entry.getValue();
        if (item == null) continue;

        CachedItemState s = item.getState();
        String key = entry.getKey().key();

        if (s == CachedItemState.DELETED || s == CachedItemState.PURGED) {
          completeSet.remove(key);
        } else if (s == CachedItemState.MODIFIED || s == CachedItemState.STORED) {
          if (allowInjection || completeSet.containsKey(key)) {
            completeSet.put(key, item.getValue());
          }
        }
      }

      this.mergedIterator =
          completeSet.entrySet().stream()
              .map(e -> (KeyValue) new SimpleKeyValue(e.getKey(), e.getValue()))
              .iterator();
    }

    @Override
    public void close() throws Exception {
      if (ledgerIterator != null) ledgerIterator.close();
    }

    @Override
    public Iterator<KeyValue> iterator() {
      return mergedIterator;
    }
  }

  private static class SimpleKeyValue implements KeyValue {
    private final String key;
    private final byte[] value;

    SimpleKeyValue(String k, byte[] v) {
      this.key = k;
      this.value = v;
    }

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
      return value != null ? new String(value) : null;
    }
  }
}
