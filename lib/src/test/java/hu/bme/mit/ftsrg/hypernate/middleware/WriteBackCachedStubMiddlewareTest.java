/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
class WriteBackCachedStubMiddlewareTest {

  private static final String KEY_1 = "key-1";
  private static final String KEY_2 = "key-2";
  private static final byte[] VALUE_1 = {1, 2, 3};
  private static final byte[] VALUE_2 = {4, 5, 6};

  @Mock private ChaincodeStub nextStub;

  private WriteBackCachedStubMiddleware middleware;

  @BeforeEach
  void setUp() {
    middleware = new WriteBackCachedStubMiddleware();
    middleware.nextStub = nextStub;
  }

  @Nested
  class given_write_back_cached_stub_middleware {

    @Test
    void putState_caches_locally_without_hitting_underlying_stub() {
      middleware.putState(KEY_1, VALUE_1);

      then(nextStub).should(never()).putState(anyString(), any(byte[].class));
      then(nextStub).shouldHaveNoInteractions();
    }

    @Test
    void getState_returns_cached_value_after_putState() {
      middleware.putState(KEY_1, VALUE_1);

      byte[] value = middleware.getState(KEY_1);

      assertArrayEquals(VALUE_1, value);
      then(nextStub).should(never()).getState(anyString());
      then(nextStub).shouldHaveNoInteractions();
    }

    @Test
    void delState_followed_by_getState_returns_null() {
      middleware.putState(KEY_1, VALUE_1);
      middleware.delState(KEY_1);

      byte[] value = middleware.getState(KEY_1);

      assertNull(value);
    }

    @Test
    void dispose_flushes_dirty_entries_to_underlying_stub() {
      middleware.putState(KEY_1, VALUE_1);
      middleware.putState(KEY_2, VALUE_2);

      middleware.dispose();

      then(nextStub).should().putState(KEY_1, VALUE_1);
      then(nextStub).should().putState(KEY_2, VALUE_2);
    }

    @Test
    void getState_falls_through_to_underlying_stub_on_cache_miss() {
      given(nextStub.getState(KEY_1)).willReturn(VALUE_1);

      byte[] value = middleware.getState(KEY_1);

      assertArrayEquals(VALUE_1, value);
      then(nextStub).should().getState(KEY_1);
    }
  }
}
