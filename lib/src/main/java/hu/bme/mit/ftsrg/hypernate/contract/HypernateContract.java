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

/** Contract base class enriched with default before-/after-transaction notification handling. */
public interface HypernateContract extends ContractInterface {

  @Override
  default Context createContext(ChaincodeStub fabricStub) {
    StubMiddlewareChain mwChain = initMiddlewares(fabricStub);
    HypernateContext ctx = new HypernateContext(mwChain);
    mwChain.forEach(ctx::subscribeToNotifications);
    return ctx;
  }

  @Override
  default void beforeTransaction(Context ctx) {
    if (ctx instanceof HypernateContext hypCtx) {
      hypCtx.notify(new TransactionBegin());
    } else {
      ContractInterface.super.beforeTransaction(ctx);
    }
  }

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
