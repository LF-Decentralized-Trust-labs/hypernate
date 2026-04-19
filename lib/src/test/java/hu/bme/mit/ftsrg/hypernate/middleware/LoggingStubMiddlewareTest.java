/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class LoggingStubMiddlewareTest {

  @Mock ChaincodeStub fabricStub;
  @Mock Logger logger;
  @Mock LoggingEventBuilder loggingEventBuilder;

  LoggingStubMiddleware middleware;

  @BeforeEach
  void setUp() {
    lenient().when(logger.atLevel(any(Level.class))).thenReturn(loggingEventBuilder);
    middleware = new LoggingStubMiddleware(logger, Level.DEBUG);
    middleware.nextStub = fabricStub;
  }

  @Test
  void getState_delegates_to_next_stub_correctly() {
    byte[] expected = "value".getBytes(StandardCharsets.UTF_8);
    when(fabricStub.getState("key1")).thenReturn(expected);

    byte[] result = middleware.getState("key1");

    assertArrayEquals(expected, result);
    verify(fabricStub).getState("key1");
  }

  @Test
  void putState_delegates_to_next_stub_correctly() {
    byte[] value = "value".getBytes(StandardCharsets.UTF_8);

    middleware.putState("key1", value);

    verify(fabricStub).putState("key1", value);
  }

  @Test
  void delState_delegates_to_next_stub_correctly() {
    middleware.delState("key1");

    verify(fabricStub).delState("key1");
  }

  @Test
  void no_calls_are_silently_swallowed() {
    byte[] value = "data".getBytes(StandardCharsets.UTF_8);
    when(fabricStub.getState("k")).thenReturn(value);

    middleware.getState("k");
    middleware.putState("k", value);
    middleware.delState("k");

    verify(fabricStub).getState("k");
    verify(fabricStub).putState("k", value);
    verify(fabricStub).delState("k");
    verifyNoMoreInteractions(fabricStub);
  }
}
