/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import org.hyperledger.fabric.shim.ChaincodeStub;

/** Encapsulating class for a chain of {@link StubMiddleware}s. */
public record StubMiddlewareChain(ChaincodeStub fabricStub, List<StubMiddleware> middlewares) {

  /**
   * Get a builder object for a middleware chain.
   *
   * <p>Usage example:
   *
   * <pre>{@code
   * // Fabric's stub pass to eg ContractInterface#createContext
   * ChaincodeStub fabricStub;
   * var chain = ChaincodeStubMiddlewareChain.builder(fabricStub)
   *                                         .push(WriteBackCachedChaincodeStubMiddleware.class)
   *                                         .push(LoggingStubMiddleware.class)
   *                                         .build();
   * }</pre>
   *
   * <p>The resulting stub chain is:
   *
   * <pre>{@code
   * (operations) ... --> LoggingStubMiddleware --> WriteBackCachedChaincodeStubMiddleware --> fabricStub --> ... (peer)
   * }</pre>
   *
   * @param fabricStub the stub object provided by Fabric
   * @return a builder object to construct the middleware chain conveniently
   */
  public static Builder builder(final ChaincodeStub fabricStub) {
    return new Builder(fabricStub);
  }

  /**
   * Get an empty middleware chain.
   *
   * @param fabricStub the stub object provided by Fabric
   * @return an empty middleware chain
   */
  public static StubMiddlewareChain emptyChain(final ChaincodeStub fabricStub) {
    return new StubMiddlewareChain(fabricStub, Collections.emptyList());
  }

  /**
   * Get the first {@link ChaincodeStub} in the chain.
   *
   * <p>The <i>first</i> is the one handling stub methods <i>first</i>. After that follow the rest
   * of the chain, and, finally, the Fabric stub.
   *
   * @return the first middleware in the chain or the Fabric stub if there are no middleware
   */
  public ChaincodeStub getFirst() {
    if (middlewares.isEmpty()) {
      return fabricStub;
    } else {
      return middlewares.get(0);
    }
  }

  /**
   * Iterate over the list of {@link StubMiddleware}s in the chain.
   *
   * <p>Iteration order follows chaining order.
   *
   * @param consumer lambda to execute for each middleware in the chain.
   */
  public void forEach(final Consumer<StubMiddleware> consumer) {
    middlewares.forEach(consumer);
  }

  /** Builder for a chaincode stub middleware chain. */
  public static class Builder {

    private final ChaincodeStub fabricStub;

    private final List<StubMiddleware> middlewares = new ArrayList<>();

    Builder(ChaincodeStub fabricStub) {
      this.fabricStub = fabricStub;
    }

    /**
     * Add another {@link StubMiddleware} to the chain.
     *
     * <p>This method will automatically link this and the previously added middleware (or the
     * Fabric stub) together.
     *
     * @param middlewareClass the type of {@link StubMiddleware} to add -- will be instantiated
     *     using a no-arg constructor
     */
    public Builder push(Class<? extends StubMiddleware> middlewareClass) {
      Constructor<? extends StubMiddleware> constructor;
      try {
        constructor = middlewareClass.getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("Could not find no-arg constructor for " + middlewareClass, e);
      }

      StubMiddleware middlewareInstance;
      try {
        middlewareInstance = constructor.newInstance();
      } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
        throw new RuntimeException("Failed to instantiate " + middlewareClass, e);
      }

      return push(middlewareInstance);
    }

    public Builder push(StubMiddleware middleware) {
      chainInMiddleware(middleware);
      return this;
    }

    /**
     * Build the middleware chain.
     *
     * @return the middleware chain with all the {@link #push(Class) add}ed {@link StubMiddleware}s.
     */
    public StubMiddlewareChain build() {
      return new StubMiddlewareChain(fabricStub, middlewares);
    }

    private void chainInMiddleware(StubMiddleware mw) {
      mw.nextStub = middlewares.isEmpty() ? fabricStub : middlewares.get(0);
      middlewares.add(0, mw);
    }
  }
}
