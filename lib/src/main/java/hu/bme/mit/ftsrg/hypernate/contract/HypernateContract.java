/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.contract;

import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.middleware.LoggingConfig;
import hu.bme.mit.ftsrg.hypernate.middleware.LoggingStubMiddleware;
import hu.bme.mit.ftsrg.hypernate.middleware.MiddlewareInfo;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddleware;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddlewareChain;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionBegin;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionEnd;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

    LoggingConfig loggingConfig = getClass().getAnnotation(LoggingConfig.class);
    Class<? extends StubMiddleware>[] middlewareClasses = mwInfoAnnot.value();
    StubMiddlewareChain.Builder builder = StubMiddlewareChain.builder(fabricStub);
    Arrays.stream(middlewareClasses)
        .map(middlewareClass -> instantiateMiddleware(middlewareClass, loggingConfig))
        .forEach(builder::push);

    return builder.build();
  }

  private StubMiddleware instantiateMiddleware(
      final Class<? extends StubMiddleware> middlewareClass, final LoggingConfig loggingConfig) {
    if (middlewareClass == LoggingStubMiddleware.class && loggingConfig != null) {
      return LoggingStubMiddleware.fromAnnotation(loggingConfig);
    }

    Constructor<? extends StubMiddleware> constructor;
    try {
      constructor = middlewareClass.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Could not find no-arg constructor for " + middlewareClass, e);
    }

    try {
      return constructor.newInstance();
    } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
      throw new RuntimeException("Failed to instantiate " + middlewareClass, e);
    }
  }
}
