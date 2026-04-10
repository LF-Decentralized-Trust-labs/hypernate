/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class BlindWriteGuardMiddlewareTest {

  @Mock private ChaincodeStub nextStub;

  private BlindWriteGuardMiddleware middleware;

  @BeforeEach
  void setUp() {
    middleware = new BlindWriteGuardMiddleware();
    middleware.nextStub = nextStub;
  }

  @Test
  void when_get_state_then_adds_to_known_keys_and_calls_next_stub() {
    final String key = "foo";
    final byte[] value = "bar".getBytes();
    given(nextStub.getState(key)).willReturn(value);

    byte[] result = middleware.getState(key);

    assertArrayEquals(value, result);
    then(nextStub).should().getState(key);
    verifyNoMoreInteractions(nextStub);
  }

  @Nested
  class given_blind_write {

    @Test
    void when_put_state_then_calls_get_state_first() {
      final String key = "foo";
      final byte[] value = "bar".getBytes();
      final byte[] existingValue = "baz".getBytes();

      given(nextStub.getState(key)).willReturn(existingValue);

      middleware.putState(key, value);

      InOrder inOrder = Mockito.inOrder(nextStub);
      inOrder.verify(nextStub).getState(key);
      inOrder.verify(nextStub).putState(key, value);
      inOrder.verifyNoMoreInteractions();
    }
  }

  @Nested
  class given_known_key {

    @Test
    void when_put_state_then_only_calls_put_state() {
      final String key = "foo";
      final byte[] value = "bar".getBytes();
      final byte[] existingValue = "baz".getBytes();

      given(nextStub.getState(key)).willReturn(existingValue);

      // First call to getState makes it a known key
      middleware.getState(key);

      // Secondary call to putState
      middleware.putState(key, value);

      InOrder inOrder = Mockito.inOrder(nextStub);
      inOrder.verify(nextStub).getState(key);
      inOrder.verify(nextStub).putState(key, value);
      inOrder.verifyNoMoreInteractions();
    }

    @Test
    void when_multiple_put_states_then_only_calls_get_state_once() {
      final String key = "foo";
      final byte[] value1 = "bar1".getBytes();
      final byte[] value2 = "bar2".getBytes();
      final byte[] existingValue = "baz".getBytes();

      given(nextStub.getState(key)).willReturn(existingValue);

      middleware.putState(key, value1);
      middleware.putState(key, value2);

      InOrder inOrder = Mockito.inOrder(nextStub);
      inOrder.verify(nextStub).getState(key);
      inOrder.verify(nextStub).putState(key, value1);
      inOrder.verify(nextStub).putState(key, value2);
      inOrder.verifyNoMoreInteractions();
    }
  }
}
