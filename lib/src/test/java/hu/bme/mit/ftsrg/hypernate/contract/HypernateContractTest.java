/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.contract;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;

import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionBegin;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionEnd;
import org.hyperledger.fabric.contract.Context;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class HypernateContractTest {

  @Mock Context nonHypernateCtx;
  @Mock HypernateContext hypernateCtx;

  @Test
  void after_transaction_delegates_to_super_for_non_hypernate_context() {
    HypernateContract contract = new TestContract();

    assertDoesNotThrow(() -> contract.afterTransaction(nonHypernateCtx, null));
  }

  @Test
  void after_transaction_notifies_transaction_end_for_hypernate_context() {
    HypernateContract contract = new TestContract();

    contract.afterTransaction(hypernateCtx, null);

    verify(hypernateCtx).notify(isA(TransactionEnd.class));
  }

  @Test
  void before_transaction_delegates_to_super_for_non_hypernate_context() {
    HypernateContract contract = new TestContract();

    assertDoesNotThrow(() -> contract.beforeTransaction(nonHypernateCtx));
  }

  @Test
  void before_transaction_notifies_transaction_begin_for_hypernate_context() {
    HypernateContract contract = new TestContract();

    contract.beforeTransaction(hypernateCtx);

    verify(hypernateCtx).notify(isA(TransactionBegin.class));
  }

  private static class TestContract implements HypernateContract {}
}
