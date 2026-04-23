/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class LoggingStubMiddlewareTest {

  private static final String KEY = "key";
  private static final byte[] VALUE = {7, 8, 9};

  @Mock private ChaincodeStub nextStub;

  private LoggingStubMiddleware middleware;

  @BeforeEach
  void setUp() {
    middleware = new LoggingStubMiddleware();
    middleware.nextStub = nextStub;
  }

  @Nested
  class given_logging_stub_middleware {

    @Test
    void getState_delegates_to_next_stub() {
      given(nextStub.getState(KEY)).willReturn(VALUE);

      byte[] value = middleware.getState(KEY);

      assertArrayEquals(VALUE, value);
      then(nextStub).should().getState(KEY);
    }

    @Test
    void putState_delegates_to_next_stub() {
      middleware.putState(KEY, VALUE);

      then(nextStub).should().putState(KEY, VALUE);
    }

    @Test
    void delState_delegates_to_next_stub() {
      middleware.delState(KEY);

      then(nextStub).should().delState(KEY);
    }

    @Test
    void no_calls_are_silently_swallowed() {
      given(nextStub.getState(KEY)).willReturn(VALUE);

      middleware.getState(KEY);
      middleware.putState(KEY, VALUE);
      middleware.delState(KEY);

      then(nextStub).should().getState(KEY);
      then(nextStub).should().putState(KEY, VALUE);
      then(nextStub).should().delState(KEY);
    }
  }
}
