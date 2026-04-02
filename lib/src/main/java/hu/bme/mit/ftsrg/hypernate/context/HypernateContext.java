/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.context;

import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddleware;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddlewareChain;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.HypernateNotification;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import lombok.Getter;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeStub;

/**
 * Context enriched with {@link Registry} and {@link StubMiddleware}s
 *
 * <p>The registry can be used to manage entities.
 */
public class HypernateContext extends Context {

  @Getter private final ChaincodeStub fabricStub;

  @Getter private final StubMiddlewareChain middlewareChain;

  private final List<Subscriber<? super HypernateNotification>> subscribers = new LinkedList<>();

  private final Flow.Publisher<HypernateNotification> notificationPublisher = subscribers::add;

  @Getter private final Registry registry;

  public HypernateContext(final StubMiddlewareChain middlewareChain) {
    super(middlewareChain.getFirst());
    this.middlewareChain = middlewareChain;
    this.fabricStub = middlewareChain.fabricStub();
    this.registry = new Registry(middlewareChain.getFirst());
  }

  /**
   * Send a {@link HypernateNotification}.
   *
   * <p>Notifies all middlewares in the chain (in the order in which they have been added).
   *
   * @param notification the notification to send
   */
  public void notify(final HypernateNotification notification) {
    subscribers.forEach(s -> s.onNext(notification));
  }

  public void subscribeToNotifications(final Subscriber<HypernateNotification> subscriber) {
    notificationPublisher.subscribe(subscriber);
  }
}
