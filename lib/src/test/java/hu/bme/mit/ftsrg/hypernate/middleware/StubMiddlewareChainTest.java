/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.*;

import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class StubMiddlewareChainTest {

  @Mock ChaincodeStub fabricStub;
  @Mock StubMiddleware testMiddleware;

  @Test
  void when_empty_chain_then_only_contains_fabric_stub() {
    final StubMiddlewareChain chain = StubMiddlewareChain.emptyChain(fabricStub);

    assertEquals(fabricStub, chain.getFirst());
    assertEquals(fabricStub, chain.fabricStub());
    assertTrue(chain.middlewares().isEmpty());
  }

  @Nested
  class given_builder {

    StubMiddlewareChain.Builder builder;

    @BeforeEach
    void setUp() {
      builder = StubMiddlewareChain.builder(fabricStub);
    }

    @Test
    void when_nothing_added_then_contains_only_fabric_stub() {
      final StubMiddlewareChain chain = builder.build();

      assertEquals(fabricStub, chain.getFirst());
      assertEquals(fabricStub, chain.fabricStub());
      assertTrue(chain.middlewares().isEmpty());
    }

    @Test
    void when_something_pushed_then_it_becomes_first() {
      builder.push(testMiddleware);
      final StubMiddlewareChain chain = builder.build();

      assertEquals(testMiddleware, chain.getFirst());
      assertEquals(fabricStub, chain.fabricStub());
      assertEquals(1, chain.middlewares().size());
    }
  }
}
