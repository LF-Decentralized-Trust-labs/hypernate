/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import hu.bme.mit.ftsrg.hypernate.middleware.WriteBackCachedStubMiddleware.PreemptiveReadPolicy;
import hu.bme.mit.ftsrg.hypernate.middleware.WriteBackCachedStubMiddleware.RestorationPolicy;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayNameGeneration(ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class WriteBackCachedStubMiddlewareTest {

  @Mock private ChaincodeStub nextStub;

  private WriteBackCachedStubMiddleware middleware;

  @BeforeEach
  void setUp() {
    middleware = new WriteBackCachedStubMiddleware();
    middleware.nextStub = nextStub; 
  }

  @Test
  void test_absent_plus_get_to_STORED_then_flush() {
    given(nextStub.getState("A")).willReturn("A_VAL".getBytes());

    byte[] res = middleware.getState("A");
    assertArrayEquals("A_VAL".getBytes(), res);

    middleware.dispose();
    verify(nextStub).getState("A");
    verifyNoMoreInteractions(nextStub);
  }

  @Test
  void test_absent_plus_put_blind_write_safety_enabled() {
    given(nextStub.getState("B")).willReturn("B_OLD".getBytes());

    middleware.putState("B", "B_NEW".getBytes());
    middleware.dispose();

    InOrder inOrder = Mockito.inOrder(nextStub);
    inOrder.verify(nextStub).getState("B");
    inOrder.verify(nextStub).putState("B", "B_NEW".getBytes());
    inOrder.verifyNoMoreInteractions();
  }
  
  @Test
  void test_absent_plus_put_blind_write_safety_disabled() {
    middleware.setPreemptiveReadPolicy(PreemptiveReadPolicy.DISABLED);
    middleware.putState("B", "B_NEW".getBytes());
    middleware.dispose();

    InOrder inOrder = Mockito.inOrder(nextStub);
    inOrder.verify(nextStub).putState("B", "B_NEW".getBytes());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void test_STORED_plus_put_to_MODIFIED_then_flush() {
    given(nextStub.getState("C")).willReturn("C_OLD".getBytes());

    middleware.getState("C");
    middleware.putState("C", "C_NEW".getBytes());

    middleware.dispose();
    InOrder inOrder = Mockito.inOrder(nextStub);
    inOrder.verify(nextStub).getState("C");
    inOrder.verify(nextStub).putState("C", "C_NEW".getBytes());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void test_STORED_plus_delete_to_DELETED_then_flush() {
    given(nextStub.getState("D")).willReturn("D_OLD".getBytes());

    middleware.getState("D");
    middleware.delState("D");

    middleware.dispose();
    InOrder inOrder = Mockito.inOrder(nextStub);
    inOrder.verify(nextStub).getState("D");
    inOrder.verify(nextStub).delState("D");
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void test_restoration_policy_deny_throws_on_put_after_delete() {
    middleware.setPreemptiveReadPolicy(PreemptiveReadPolicy.DISABLED);
    middleware.putState("E", "E_NEW".getBytes());
    middleware.delState("E");

    assertThrows(IllegalStateException.class, () -> middleware.putState("E", "E_RESTORE".getBytes()));
  }

  @Test
  void test_restoration_policy_allow_restores_put_after_delete() {
    middleware.setPreemptiveReadPolicy(PreemptiveReadPolicy.DISABLED);
    middleware.setRestorationPolicy(RestorationPolicy.ALLOW);
    
    middleware.putState("E", "E_NEW".getBytes());
    middleware.delState("E");
    middleware.putState("E", "E_RESTORE".getBytes());

    middleware.dispose();
    InOrder inOrder = Mockito.inOrder(nextStub);
    inOrder.verify(nextStub).putState("E", "E_RESTORE".getBytes());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void test_flush_correctness_private_data() {
    given(nextStub.getPrivateData("coll", "key")).willReturn("P_OLD".getBytes());

    middleware.getPrivateData("coll", "key");
    middleware.putPrivateData("coll", "key", "P_NEW".getBytes());
    
    middleware.setPreemptiveReadPolicy(PreemptiveReadPolicy.DISABLED);
    middleware.putPrivateData("coll", "key2", "P_NEW2".getBytes());
    middleware.delPrivateData("coll", "key");
    middleware.purgePrivateData("coll", "key3");

    middleware.dispose();

    verify(nextStub).getPrivateData("coll", "key");
    verify(nextStub).delPrivateData("coll", "key");
    verify(nextStub).putPrivateData("coll", "key2", "P_NEW2".getBytes());
    verify(nextStub).purgePrivateData("coll", "key3");
  }

  @Test
  void test_query_merging_populate_and_injection() throws Exception {
    QueryResultsIterator<KeyValue> mockIter = mock(QueryResultsIterator.class);
    List<KeyValue> ledgerMockElements = new ArrayList<>();
    ledgerMockElements.add(new TestKeyValue("1", "old1"));
    ledgerMockElements.add(new TestKeyValue("2", "old2"));
    ledgerMockElements.add(new TestKeyValue("3", "old3"));

    given(nextStub.getStateByRange("1", "4")).willReturn(mockIter);
    given(mockIter.iterator()).willReturn(ledgerMockElements.iterator());

    middleware.setPreemptiveReadPolicy(PreemptiveReadPolicy.DISABLED);
    middleware.putState("2", "new2".getBytes());
    middleware.delState("3");
    middleware.putState("3.5", "new3.5".getBytes());

    QueryResultsIterator<KeyValue> qri = middleware.getStateByRange("1", "4");
    List<KeyValue> result = new ArrayList<>();
    qri.forEachRemaining(result::add);

    assertEquals(3, result.size()); 
    // Validating '1' is stored appropriately
    given(nextStub.getState("1")).willThrow(new IllegalStateException("Ledger queried again instead of cache!"));
    assertArrayEquals("old1".getBytes(), middleware.getState("1"));

    assertEquals("1", result.get(0).getKey());
    assertEquals("2", result.get(1).getKey());
    assertEquals("new2", new String(result.get(1).getValue()));
    assertEquals("3.5", result.get(2).getKey());
    assertEquals("new3.5", new String(result.get(2).getValue()));
  }

  @Test
  void test_query_merging_pagination_no_injection() throws Exception {
    QueryResultsIteratorWithMetadata<KeyValue> mockIter = mock(QueryResultsIteratorWithMetadata.class);
    List<KeyValue> ledgerElements = new ArrayList<>();
    ledgerElements.add(new TestKeyValue("1", "old1"));
    
    given(nextStub.getStateByRangeWithPagination("1", "4", 10, "bm")).willReturn(mockIter);
    given(mockIter.iterator()).willReturn(ledgerElements.iterator());

    middleware.setPreemptiveReadPolicy(PreemptiveReadPolicy.DISABLED);
    middleware.putState("1.5", "injected_but_filtered".getBytes()); 

    QueryResultsIteratorWithMetadata<KeyValue> qri = middleware.getStateByRangeWithPagination("1", "4", 10, "bm");
    List<KeyValue> result = new ArrayList<>();
    qri.forEachRemaining(result::add);

    assertEquals(1, result.size()); 
    assertEquals("1", result.get(0).getKey());
  }

  static class TestKeyValue implements KeyValue {
    private final String key;
    private final String value;

    TestKeyValue(String k, String v) {
      this.key = k;
      this.value = v;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public byte[] getValue() {
      return value.getBytes();
    }

    @Override
    public String getStringValue() {
      return value;
    }
  }
}
