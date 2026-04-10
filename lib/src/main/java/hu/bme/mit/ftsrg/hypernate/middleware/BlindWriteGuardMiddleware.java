/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import com.jcabi.aspects.Loggable;
import java.util.HashSet;
import java.util.Set;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub middleware that guards against blind writes.
 *
 * <p>A blind write is when {@link ChaincodeStub#putState(String, byte[])} is called before the key
 * has been read by the middleware chain. This middleware guarantees that {@code getState} is called
 * internally for missing keys before the write goes through, forcing caching downstream.
 *
 * @see StubMiddleware
 */
@Loggable(Loggable.DEBUG)
public final class BlindWriteGuardMiddleware extends StubMiddleware {

  private final Logger logger = LoggerFactory.getLogger(BlindWriteGuardMiddleware.class);

  private final Set<String> knownKeys = new HashSet<>();

  @Override
  public byte[] getState(final String key) {
    knownKeys.add(key);
    return this.nextStub.getState(key);
  }

  @Override
  public void putState(final String key, final byte[] value) {
    if (!knownKeys.contains(key)) {
      logger.debug(
          "Guard intercepted blind write for key={}; triggering downstream read", key);
      
      this.nextStub.getState(key);
      knownKeys.add(key);
    }
    
    this.nextStub.putState(key, value);
  }
}
