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
 * Context enriched with a {@link Registry} and {@link StubMiddleware}s.
 *
 * <p>The registry can be used to manage entities.
 */
public class HypernateContext extends Context {

  /**
   * The 'original' Fabric chaincode stub.
   *
   * <p>Usually, you should use the {@link #getRegistry() registry}) instead of the stub directly.
   *
   * @return the 'original' Fabric chaincode stub
   */
  @Getter private final ChaincodeStub fabricStub;

  /**
   * The array of defined stub middleware.
   *
   * <p>You usually do not have to interact with the middlewares once you have defined them.
   *
   * @return the array of defined stub middleware
   */
  @Getter private final StubMiddlewareChain middlewareChain;

  private final List<Subscriber<? super HypernateNotification>> subscribers = new LinkedList<>();

  private final Flow.Publisher<HypernateNotification> notificationPublisher = subscribers::add;

  /**
   * Get the {@link Registry} object in this context.
   *
   * <p>When using Hypernate, you likely want to perform most ledger operations via this {@link
   * Registry registry} instance in lieu of using the {@link #getFabricStub() stub}.
   *
   * @return the {@link Registry} object in this context
   */
  @Getter private final Registry registry;

  /**
   * Create a context instance given a chain of {@link StubMiddleware middleware}.
   *
   * @param middlewareChain the middleware chain to use in this context
   */
  public HypernateContext(final StubMiddlewareChain middlewareChain) {
    super(middlewareChain.getFirst());
    this.middlewareChain = middlewareChain;
    this.fabricStub = middlewareChain.fabricStub();
    this.registry = new Registry(middlewareChain.getFirst());
  }

  /**
   * {@inheritDoc}
   *
   * <p><strong>WARNING:</strong> with Hypernate, this is not necessarily the 'original' Fabric stub
   * you are expecting. You will get the <em>first</em> middleware's stub in the middleware chain.
   * If you want the 'lowest-level' stub, you should use {@link #getFabricStub()}.
   *
   * @return the first stub in the middleware chain
   */
  @Override
  public ChaincodeStub getStub() {
    return super.getStub();
  }

  /**
   * Send a {@link HypernateNotification notification}.
   *
   * <p>Notifies all middlewares in the chain (in the order in which they have been added).
   *
   * @param notification the notification to send
   */
  public void notify(final HypernateNotification notification) {
    subscribers.forEach(s -> s.onNext(notification));
  }

  /**
   * Add a new {@link HypernateNotification notification} subscriber.
   *
   * @param subscriber the subscribing object
   */
  public void subscribeToNotifications(final Subscriber<HypernateNotification> subscriber) {
    notificationPublisher.subscribe(subscriber);
  }
}
