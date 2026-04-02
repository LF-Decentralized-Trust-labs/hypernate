/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.HypernateNotification;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionBegin;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionEnd;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import lombok.experimental.Delegate;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChaincodeStub} middlewares to be chained.
 *
 * <p>Stub middleware classes provide the same interface as {@link ChaincodeStub}, but under the
 * hood they maintain a reference to another {@link ChaincodeStub} and delegate all calls to that.
 * You can override any method in this class to inject your custom behaviour, such as logging,
 * access control, caching, etc.
 */
@Loggable(Loggable.DEBUG)
public abstract class StubMiddleware implements ChaincodeStub, Subscriber<HypernateNotification> {

  private final Logger logger = LoggerFactory.getLogger(StubMiddleware.class);

  /** The next {@link ChaincodeStub} in the chain. */
  @Delegate ChaincodeStub nextStub;

  @Override
  public void onSubscribe(Subscription subscription) {}

  @Override
  public void onNext(HypernateNotification notification) {
    if (notification instanceof TransactionBegin) {
      onTransactionBegin();
    } else if (notification instanceof TransactionEnd) {
      onTransactionEnd();
    } else {
      onNotification(notification);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    logger.error(throwable.getMessage(), throwable);
  }

  @Override
  public void onComplete() {}

  /**
   * Hypernate notification listener.
   *
   * <p>You can override this method to react to any {@link HypernateNotification}s received. For
   * example:
   *
   * <pre>{@code
   * @Override
   * protected void onEvent(final HypernateNotification notification) {
   *   if (notification instanceof MyCustomNotification) {
   *     System.out.println("Handling custom notification");
   *   } else {
   *     System.out.println("Received non-handled notification – ignoring it");
   *   }
   * }
   * }</pre>
   *
   * @param notification notification object
   */
  @SuppressWarnings("EmptyMethod")
  public void onNotification(final HypernateNotification notification) {}

  /** Convenience method to for handling the {@link TransactionBegin} notification. */
  @SuppressWarnings("EmptyMethod")
  protected void onTransactionBegin() {}

  /** Convenience method to for handling the {@link TransactionEnd} notification. */
  @SuppressWarnings("EmptyMethod")
  protected void onTransactionEnd() {}
}
