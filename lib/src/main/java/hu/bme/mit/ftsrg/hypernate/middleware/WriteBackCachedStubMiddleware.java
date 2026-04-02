/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionEnd;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub middleware that caches reads in a local state.
 *
 * @see StubMiddleware
 */
@Loggable(Loggable.DEBUG)
public final class WriteBackCachedStubMiddleware extends StubMiddleware {

  private final Logger logger = LoggerFactory.getLogger(WriteBackCachedStubMiddleware.class);

  private final Map<String, CachedItem> cache = new HashMap<>();

  /**
   * Get the raw state at {@code key} but only call down to the peer if we have not seen the value
   * at {@code key} before.
   *
   * @param key the queried key
   * @return the raw state at {@code key}
   */
  @Override
  public byte[] getState(final String key) {
    CachedItem cached = cache.get(key);

    // New read, add to cache
    if (cached == null) {
      logger.debug("Cache miss for key={} while reading; getting from next layer & caching", key);
      final byte[] value = this.nextStub.getState(key);
      cached = new CachedItem(key, value);
      cache.put(key, cached);
    }

    // Already marked for deletion
    if (cached.isToDelete()) {
      logger.debug("Value at key={} marked for deletion; returning null", key);
      return null;
    }

    return cached.getValue();
  }

  /**
   * Write raw state passed in {@code value} at {@code key} but instead of doing it directly, only
   * update the cache for now.
   *
   * <p>The {@link ChaincodeStub#putState(String, byte[])} call will only actually occur during
   * {@link #dispose()}.
   *
   * @param key the key to write
   * @param value the value to write at the key
   */
  @Override
  public void putState(final String key, final byte[] value) {
    CachedItem cached = cache.get(key);

    // Blind write!
    if (cached == null) {
      logger.debug(
          "Cache miss for key={} while writing; creating new cache entry with null value", key);
      cached = new CachedItem(key, null); // Initial value set later
      cache.put(key, cached);
    }

    if (cached.isToDelete()) {
      logger.debug("Entry at key={} already deleted; cannot update", key);
      throw new RuntimeException("Ledger entry " + key + " is already marked for deletion");
    }

    logger.debug(
        "Setting value for cache item with key={} to a {}-long byte array", key, value.length);
    cached.setValue(value); // Sets the dirty flag if needed
  }

  /**
   * Delete the value at {@code key} but only mark it as deleted in our cache for now.
   *
   * <p>The {@link ChaincodeStub#delState(String)} call will only actually occur during {@link
   * #dispose()}.
   *
   * @param key the key to delete
   */
  @Override
  public void delState(final String key) {
    CachedItem cached = cache.get(key);

    // Blind delete!
    if (cached == null) {
      logger.debug(
          "Cache miss for key={} while deleting; creating new cache entry with null value", key);
      cached = new CachedItem(key, null);
      cache.put(key, cached);
    }

    logger.debug("Deleting value from cache with key={}", key);
    cached.delete();
  }

  /**
   * Apply the cache changes.
   *
   * <p>This method should normally be called in a handler for the {@link TransactionEnd}
   * notification.
   */
  public void dispose() {
    for (final Map.Entry<String, CachedItem> entry : cache.entrySet()) {
      final CachedItem item = entry.getValue();

      if (item == null || !item.isDirty() || item.getValue() == null) continue;

      if (item.isToDelete()) this.nextStub.delState(item.getKey());
      else this.nextStub.putState(item.getKey(), item.getValue());
    }
  }

  @Getter
  @Loggable(Loggable.DEBUG)
  private static final class CachedItem {

    private final String key;
    private byte[] value;
    private boolean toDelete = false;
    private boolean dirty = false;

    CachedItem(final String key, final byte[] value) {
      this.key = key;
      this.value = value;
    }

    public void setValue(final byte[] value) {
      if (Arrays.equals(this.value, value)) return;

      this.value = value;
      this.dirty = true;
    }

    public void delete() {
      if (!this.toDelete) {
        this.toDelete = true;
        this.dirty = true;
      }
    }

    public boolean hasValue() {
      return this.value != null;
    }
  }
}
