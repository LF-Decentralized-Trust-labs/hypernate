/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class WriteBackCachedStubMiddlewareTest {

  @Mock ChaincodeStub fabricStub;

  WriteBackCachedStubMiddleware middleware;

  @BeforeEach
  void setUp() {
    middleware = new WriteBackCachedStubMiddleware();
    middleware.nextStub = fabricStub;
  }

  @Test
  void putState_caches_locally_without_hitting_underlying_stub() {
    byte[] value = "hello".getBytes(StandardCharsets.UTF_8);

    middleware.putState("key1", value);

    verifyNoInteractions(fabricStub);
  }

  @Test
  void getState_returns_cached_value_after_putState() {
    byte[] value = "cached-value".getBytes(StandardCharsets.UTF_8);

    middleware.putState("key1", value);
    byte[] result = middleware.getState("key1");

    assertArrayEquals(value, result);
    verifyNoInteractions(fabricStub);
  }

  @Test
  void delState_followed_by_getState_returns_null_from_cache() {
    byte[] value = "to-delete".getBytes(StandardCharsets.UTF_8);

    middleware.putState("key1", value);
    middleware.delState("key1");
    byte[] result = middleware.getState("key1");

    assertNull(result);
    verifyNoInteractions(fabricStub);
  }

  @Test
  void dispose_flushes_all_dirty_entries_to_underlying_stub() {
    byte[] val1 = "value1".getBytes(StandardCharsets.UTF_8);
    byte[] val2 = "value2".getBytes(StandardCharsets.UTF_8);

    middleware.putState("key1", val1);
    middleware.putState("key2", val2);
    middleware.delState("key2");

    middleware.dispose();

    verify(fabricStub).putState("key1", val1);
    verify(fabricStub).delState("key2");
    verifyNoMoreInteractions(fabricStub);
  }

  @Test
  void getState_falls_through_to_underlying_stub_on_cache_miss() {
    byte[] expected = "from-ledger".getBytes(StandardCharsets.UTF_8);
    when(fabricStub.getState("key1")).thenReturn(expected);

    byte[] result = middleware.getState("key1");

    assertArrayEquals(expected, result);
    verify(fabricStub).getState("key1");
  }
}
