/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class LoggingStubMiddlewareTest {

  @Mock private ChaincodeStub fabricStub;

  private LoggingStubMiddleware loggingMiddleware;

  @BeforeEach
  void setUp() {
    loggingMiddleware = new LoggingStubMiddleware();
    loggingMiddleware.nextStub = fabricStub;
  }

  @Test
  void when_getState_then_delegates_to_nextStub() {
    byte[] expected = "value".getBytes();
    given(fabricStub.getState("key1")).willReturn(expected);

    byte[] actual = loggingMiddleware.getState("key1");

    assertArrayEquals(expected, actual);
    then(fabricStub).should().getState("key1");
  }

  @Test
  void when_putState_then_delegates_to_nextStub() {
    byte[] value = "value".getBytes();

    loggingMiddleware.putState("key1", value);

    then(fabricStub).should().putState("key1", value);
  }

  @Test
  void when_delState_then_delegates_to_nextStub() {
    loggingMiddleware.delState("key1");

    then(fabricStub).should().delState("key1");
  }
}
