/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
class WriteBackCachedStubMiddlewareTest {

  @Mock private ChaincodeStub fabricStub;

  private WriteBackCachedStubMiddleware cachedMiddleware;

  @BeforeEach
  void setUp() {
    cachedMiddleware = new WriteBackCachedStubMiddleware();
    cachedMiddleware.nextStub = fabricStub;
  }

  @Test
  void given_cache_miss_on_getState_then_fetches_from_stub_and_caches() {
    byte[] expected = "val1".getBytes();
    given(fabricStub.getState("key1")).willReturn(expected);

    byte[] res1 = cachedMiddleware.getState("key1");
    byte[] res2 = cachedMiddleware.getState("key1");

    assertArrayEquals(expected, res1);
    assertArrayEquals(expected, res2);
    then(fabricStub).should().getState("key1"); // Only called once due to caching
  }

  @Test
  void given_putState_then_caches_without_calling_stub_until_dispose() {
    byte[] value = "newVal".getBytes();

    cachedMiddleware.putState("key1", value);

    then(fabricStub).should(never()).putState("key1", value);

    cachedMiddleware.dispose();

    then(fabricStub).should().putState("key1", value);
  }

  @Test
  void given_delState_then_marks_deleted_and_deletes_on_dispose() {
    cachedMiddleware.delState("key1");

    assertNull(cachedMiddleware.getState("key1"));

    then(fabricStub).should(never()).delState("key1");

    cachedMiddleware.dispose();

    then(fabricStub).should().delState("key1");
  }
}
