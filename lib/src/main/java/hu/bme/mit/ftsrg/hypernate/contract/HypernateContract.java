/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.contract;

import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.middleware.MiddlewareInfo;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddleware;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddlewareChain;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionBegin;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionEnd;
import java.util.Arrays;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.shim.ChaincodeStub;

/**
 * Contract base class enriched with {@link HypernateContext} and default before-/after-transaction
 * notification handling.
 *
 * <p>To get started with Hypernate, just implement {@link HypernateContract} and start using {@link
 * HypernateContext#getRegistry()} in your logic.
 *
 * <blockquote>
 *
 * <pre>{@code
 * public class MyContract implements HypernateContract {
 *
 *   @Transaction(intent = Transaction.TYPE.EVALUATE)
 *   public Foo read(final HypernateContext ctx, final String id) {
 *     try {
 *       return ctx.getRegistry().mustRead(Foo.class, id);
 *     } catch(final EntityNotFoundException e) {
 *       throw new ChaincodeException(String.format("No Foo found with id %s: %s", id, e.getMessage()));
 *     }
 *   }
 * }
 * }</pre>
 *
 * </blockquote>
 */
public interface HypernateContract extends ContractInterface {

  /**
   * {@inheritDoc}
   *
   * <p>Creates a {@link HypernateContext} that has a {@link
   * hu.bme.mit.ftsrg.hypernate.registry.Registry Registry} inside.
   *
   * @param fabricStub {@link ChaincodeStub} automatically passed by Fabric
   * @return an instance of {@link HypernateContext}
   * @see HypernateContext
   */
  @Override
  default Context createContext(ChaincodeStub fabricStub) {
    StubMiddlewareChain mwChain = initMiddlewares(fabricStub);
    HypernateContext ctx = new HypernateContext(mwChain);
    mwChain.forEach(ctx::subscribeToNotifications);
    return ctx;
  }

  /**
   * Sends {@link TransactionBegin} notifications and falls back to the default {@link
   * ContractInterface#beforeTransaction(Context)} behaviour.
   *
   * @param ctx the context as created by {@link #createContext(ChaincodeStub)}.
   * @see TransactionBegin
   */
  @Override
  default void beforeTransaction(Context ctx) {
    if (ctx instanceof HypernateContext hypCtx) {
      hypCtx.notify(new TransactionBegin());
    } else {
      ContractInterface.super.beforeTransaction(ctx);
    }
  }

  /**
   * Sends {@link TransactionEnd} notifications and falls back to the default {@link
   * ContractInterface#afterTransaction(Context, Object)} behaviour.
   *
   * @param ctx the context as created by {@link #createContext(ChaincodeStub)}.
   * @see TransactionEnd
   */
  @Override
  default void afterTransaction(Context ctx, Object _result) {
    if (ctx instanceof HypernateContext hypCtx) {
      hypCtx.notify(new TransactionEnd());
    } else {
      ContractInterface.super.beforeTransaction(ctx);
    }
  }

  /**
   * Initialize the middleware chain.
   *
   * <p>Normally, Hypernate processes the {@link MiddlewareInfo} annotation on the contract class if
   * it exists. You can override this behaviour with custom middleware initialization logic by
   * overriding this method.
   *
   * @param fabricStub the stub object provided by Fabric (should normally be the last in the chain)
   * @return the middleware chain
   * @see StubMiddleware
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
