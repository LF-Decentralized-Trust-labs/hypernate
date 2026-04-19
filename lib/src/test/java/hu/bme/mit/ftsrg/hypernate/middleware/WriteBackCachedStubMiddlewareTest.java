/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.mockito.Mockito.*;

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
  void blind_delete_is_flushed_to_underlying_stub_on_dispose() {
    middleware.delState("key-never-read");

    middleware.dispose();

    verify(fabricStub).delState("key-never-read");
  }
}
