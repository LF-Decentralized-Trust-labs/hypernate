/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.contract;

import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.middleware.MiddlewareInfo;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddleware;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddlewareChain;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionBegin;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionEnd;
import java.util.*;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.slf4j.LoggerFactory;

/**
 * Contract base interface enriched with default before-/after-transaction notification handling and
 * middleware chain initialization.
 *
 * <p>Implementing classes should generally avoid overriding {@link #createContext(ChaincodeStub)},
 * {@link #beforeTransaction(Context)}, and {@link #afterTransaction(Context, Object)}, as
 * overriding these methods without reproducing the base plumbing will break Hypernate's middleware
 * chain, registry, and caching mechanisms. Instead, use {@link
 * #onBeforeTransaction(HypernateContext)}, {@link #onAfterTransaction(HypernateContext, Object)},
 * and {@link #initMiddlewares(ChaincodeStub)} to extend contract behavior.
 */
public interface HypernateContract extends ContractInterface {

  /**
   * Plumbing method: creates the {@link HypernateContext} by initializing the middleware chain,
   * wrapping it in a context, and subscribing all middlewares in the chain to transaction
   * notifications.
   *
   * <p><strong>Do not override this method.</strong> If custom middleware initialization is
   * required, override {@link #initMiddlewares(ChaincodeStub)} instead.
   *
   * @param fabricStub the fabric chaincode stub
   * @return the initialized hypernate transaction context
   */
  @Override
  default Context createContext(ChaincodeStub fabricStub) {
    StubMiddlewareChain mwChain = initMiddlewares(fabricStub);
    HypernateContext ctx = new HypernateContext(mwChain);
    mwChain.forEach(ctx::subscribeToNotifications);
    return ctx;
  }

  /**
   * Plumbing method: publishes the {@link TransactionBegin} notification and calls the {@link
   * #onBeforeTransaction(HypernateContext)} hook.
   *
   * <p><strong>Do not override this method.</strong> Override {@link
   * #onBeforeTransaction(HypernateContext)} to implement pre-transaction hooks.
   *
   * @param ctx the fabric transaction context
   */
  @Override
  default void beforeTransaction(Context ctx) {
    if (ctx instanceof HypernateContext hypCtx) {
      hypCtx.notify(new TransactionBegin());
      onBeforeTransaction(hypCtx);
    } else {
      LoggerFactory.getLogger(HypernateContract.class)
          .warn(
              "beforeTransaction called with non-HypernateContext context instance (type: {}). "
                  + "This suggests createContext was overridden without preserving Hypernate plumbing. "
                  + "Hypernate middleware and write-back caches will not function.",
              ctx.getClass().getName());
      ContractInterface.super.beforeTransaction(ctx);
    }
  }

  /**
   * Plumbing method: calls the {@link #onAfterTransaction(HypernateContext, Object)} hook and
   * publishes the {@link TransactionEnd} notification (flushing the write-back cache).
   *
   * <p><strong>Do not override this method.</strong> Override {@link
   * #onAfterTransaction(HypernateContext, Object)} to implement post-transaction hooks.
   *
   * @param ctx the fabric transaction context
   * @param _result the transaction execution result
   */
  @Override
  default void afterTransaction(Context ctx, Object _result) {
    if (ctx instanceof HypernateContext hypCtx) {
      onAfterTransaction(hypCtx, _result);
      hypCtx.notify(new TransactionEnd());
    } else {
      LoggerFactory.getLogger(HypernateContract.class)
          .warn(
              "afterTransaction called with non-HypernateContext context instance (type: {}). "
                  + "This suggests createContext was overridden without preserving Hypernate plumbing. "
                  + "Hypernate middleware and write-back caches will not function.",
              ctx.getClass().getName());
      ContractInterface.super.afterTransaction(ctx, _result);
    }
  }

  /**
   * Hook method called before a transaction execution, after middlewares have been initialized and
   * the {@link TransactionBegin} notification has been published.
   *
   * <p>Override this method to run custom setup or validation logic before each transaction.
   *
   * @param ctx the hypernate transaction context
   */
  default void onBeforeTransaction(HypernateContext ctx) {}

  /**
   * Hook method called after a transaction execution completes, before the {@link TransactionEnd}
   * notification is published (and thus before the write-back cache is flushed).
   *
   * <p>Override this method to run custom cleanup or logging logic after each transaction.
   *
   * @param ctx the hypernate transaction context
   * @param result the result of the transaction execution, or null if void/failed
   */
  default void onAfterTransaction(HypernateContext ctx, Object result) {}

  /**
   * Initialize the middleware chain.
   *
   * <p>Normally, Hypernate processes the {@link MiddlewareInfo} annotation on the contract class if
   * it exists.
   *
   * <p>You can override this behaviour with custom middleware initialization logic by overriding
   * this method.
   *
   * @param fabricStub the stub object provided by Fabric (should normally be the last in the chain)
   * @return the middleware chain
   */
  default StubMiddlewareChain initMiddlewares(final ChaincodeStub fabricStub) {
    MiddlewareInfo mwInfoAnnot = getClass().getAnnotation(MiddlewareInfo.class);
    if (mwInfoAnnot == null) {
      return StubMiddlewareChain.emptyChain(fabricStub);
    }

    Class<? extends StubMiddleware>[] middlewareClasses = mwInfoAnnot.value();
    StubMiddlewareChain.Builder builder = StubMiddlewareChain.builder(fabricStub);
    Arrays.stream(middlewareClasses).forEach(builder::push);

    return builder.build();
  }
}
