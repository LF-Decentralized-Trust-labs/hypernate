/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Arrays;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Stub middleware that simply logs all {@link ChaincodeStub#getState(String)}, {@link
 * ChaincodeStub#putState(String, byte[])}, and {@link ChaincodeStub#delState(String)} calls.
 *
 * @see StubMiddleware
 */
public class LoggingStubMiddleware extends StubMiddleware {

  private static final Tracer tracer =
      GlobalOpenTelemetry.getTracer("hu.bme.mit.ftsrg.hypernate.middleware");

  private final Logger logger;

  private final Level logLevel;

  public LoggingStubMiddleware() {
    this(LoggerFactory.getLogger(LoggingStubMiddleware.class));
  }

  public LoggingStubMiddleware(final Logger logger) {
    this(logger, Level.DEBUG);
  }

  public LoggingStubMiddleware(final Logger logger, final Level logLevel) {
    this.logger = logger;
    this.logLevel = logLevel;
  }

  /**
   * Get the raw state at {@code key} but log a message before and after doing so.
   *
   * @param key the queried key
   * @return the raw state at {@code key}
   */
  @Override
  public byte[] getState(final String key) {
    Span span = startSpan("getState", key);
    try (Scope scope = span.makeCurrent()) {
      log("Getting state for key '{}'", key);
      final byte[] value = this.nextStub.getState(key);
      log("Got state for key '{}'; value = '{}'", key, Arrays.toString(value));
      return value;
    } catch (RuntimeException ex) {
      span.recordException(ex);
      span.setStatus(StatusCode.ERROR);
      throw ex;
    } finally {
      span.end();
    }
  }

  /**
   * Write raw state passed in {@code value} at {@code key} but log a message before and after doing
   * so.
   *
   * @param key where to write {@code value}
   * @param value what to write at {@code key}
   */
  @Override
  public void putState(final String key, final byte[] value) {
    Span span = startSpan("putState", key);
    span.setAttribute("hypernate.value_length", value == null ? 0 : value.length);
    try (Scope scope = span.makeCurrent()) {
      log("Setting state for key '{}' to have value '{}'", key, Arrays.toString(value));
      this.nextStub.putState(key, value);
      log("Done setting state for key '{}'", key);
    } catch (RuntimeException ex) {
      span.recordException(ex);
      span.setStatus(StatusCode.ERROR);
      throw ex;
    } finally {
      span.end();
    }
  }

  /**
   * Delete the value at {@code key} but log a message before and after doing so.
   *
   * @param key the key whose value should be deleted
   */
  @Override
  public void delState(final String key) {
    Span span = startSpan("delState", key);
    try (Scope scope = span.makeCurrent()) {
      log("Deleting state for key '{}'", key);
      this.nextStub.delState(key);
      log("Done deleting state for key '{}'", key);
    } catch (RuntimeException ex) {
      span.recordException(ex);
      span.setStatus(StatusCode.ERROR);
      throw ex;
    } finally {
      span.end();
    }
  }

  private static Span startSpan(final String operation, final String key) {
    return tracer
        .spanBuilder("hypernate.stub." + operation)
        .setAttribute("hypernate.operation", operation)
        .setAttribute("hypernate.key", key == null ? "" : key)
        .startSpan();
  }

  private void log(final String format, Object... args) {
    logger.atLevel(logLevel).log(format, args);
  }
}
