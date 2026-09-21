/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.contract;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.HypernateNotification;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionBegin;
import hu.bme.mit.ftsrg.hypernate.middleware.notification.TransactionEnd;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import org.hyperledger.fabric.contract.Context;
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
class HypernateContractTest {

  @Mock private ChaincodeStub stub;
  @Mock private Context plainContext;

  private TestContract contract;

  @BeforeEach
  void setUp() {
    contract = new TestContract();

    String pemCert =
        "-----BEGIN CERTIFICATE-----\n"
            + "MIIC/zCCAeegAwIBAgIURRdj6Y8640fkGMq1qf1v3GXZWpIwDQYJKoZIhvcNAQEL\n"
            + "BQAwDzENMAsGA1UEAwwEdGVzdDAeFw0yNjA3MjExNjMxMDFaFw0yNzA3MjExNjMx\n"
            + "MDFaMA8xDTALBgNVBAMMBHRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n"
            + "AoIBAQCt77mk/XPOA9CAipMNlng+rTEa8yVe1uJ/eSpc80hF+GXOHtE+c423gQBX\n"
            + "aaJlyVMAefx68dJ86oeKDM/85cMnBlxLzn5r3RVzbqi99/vs+FnAb5XyIcncSd17\n"
            + "Xm4Zjq2nuoZA/WuEC5GXaqkMW1WAAoiwZDsZO4L8oB2VU/+/izzs3aRxKsjfD45k\n"
            + "vLByyu5DLhnGN+muHyMMGMPvxS/ZBe9QYxdyxfsEWTsZQ2qvxdDSwBkft0GL6ezE\n"
            + "z7q1pTbchXi/LE0n/Zper7rZalhSszgXfoMd2gdpnOVF7AGYxrPq/8tYA+u8c8x7\n"
            + "p/ZLQNfXklA3KcLW7klEqrqNX+3LAgMBAAGjUzBRMB0GA1UdDgQWBBRB2ziW2SfG\n"
            + "JrhZTTB8/5K5208U6zAfBgNVHSMEGDAWgBRB2ziW2SfGJrhZTTB8/5K5208U6zAP\n"
            + "BgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCU8JJ/uiSFjMkspQxX\n"
            + "WTXjH2belXE6pEnXV7zpVYPttapM0+2BQyn+PprPU19Lye5FdUB1L2vFEAu+X7Be\n"
            + "Z5kW/tDCWcoZ1yExOlyIh6ZPoX97fZ5XxmrFV6zjAbl4NZFPnpS3WsIaUJomL6Xo\n"
            + "8ymCWYO32IImjVlrs2AydX2ZV9jzEFLF8yMREHxHPsfkukBcISMW2eNw5Dj4Qj7N\n"
            + "mvk22/swhdu7kAdcv+TpHU0XgfLjej3bAJsb93UVvmeZRYPN2eh9sVVzKoVVUfOA\n"
            + "KHZFduAMjBvzSw5h1G6xdMA0o14aYXbhN5tpRvVWCjTOAh3yavwCELYv40wHLDFu\n"
            + "RFWW\n"
            + "-----END CERTIFICATE-----";

    org.hyperledger.fabric.protos.msp.SerializedIdentity mspIdentity =
        org.hyperledger.fabric.protos.msp.SerializedIdentity.newBuilder()
            .setMspid("testMSP")
            .setIdBytes(com.google.protobuf.ByteString.copyFromUtf8(pemCert))
            .build();
    lenient().when(stub.getCreator()).thenReturn(mspIdentity.toByteArray());
  }

  @Test
  void should_initialize_context_and_subscribe_middlewares() {
    Context context = contract.createContext(stub);
    assertTrue(context instanceof HypernateContext);

    HypernateContext hypCtx = (HypernateContext) context;
    assertEquals(stub, hypCtx.getFabricStub());
  }

  @Test
  void should_trigger_notifications_and_hooks_in_correct_order() {
    HypernateContext hypCtx = (HypernateContext) contract.createContext(stub);
    hypCtx.subscribeToNotifications(
        new Subscriber<HypernateNotification>() {
          @Override
          public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(HypernateNotification item) {
            if (item instanceof TransactionBegin) {
              contract.callOrder.add("TransactionBegin");
            } else if (item instanceof TransactionEnd) {
              contract.callOrder.add("TransactionEnd");
            }
          }

          @Override
          public void onError(Throwable throwable) {}

          @Override
          public void onComplete() {}
        });

    contract.beforeTransaction(hypCtx);
    assertEquals(2, contract.callOrder.size());
    assertEquals("TransactionBegin", contract.callOrder.get(0));
    assertEquals("onBeforeTransaction", contract.callOrder.get(1));

    Object result = "testResult";
    contract.afterTransaction(hypCtx, result);
    assertEquals(4, contract.callOrder.size());
    assertEquals("onAfterTransaction", contract.callOrder.get(2));
    assertEquals("TransactionEnd", contract.callOrder.get(3));

    assertTrue(contract.beforeHookCalled);
    assertTrue(contract.afterHookCalled);
    assertEquals(result, contract.afterResult);
  }

  @Test
  void should_fallback_when_context_is_not_hypernate_context() {
    assertDoesNotThrow(() -> contract.beforeTransaction(plainContext));
    assertDoesNotThrow(() -> contract.afterTransaction(plainContext, "result"));

    assertFalse(contract.beforeHookCalled);
    assertFalse(contract.afterHookCalled);
  }

  private static class TestContract implements HypernateContract {
    boolean beforeHookCalled = false;
    boolean afterHookCalled = false;
    Object afterResult = null;
    final List<String> callOrder = new ArrayList<>();

    @Override
    public void onBeforeTransaction(HypernateContext ctx) {
      beforeHookCalled = true;
      callOrder.add("onBeforeTransaction");
    }

    @Override
    public void onAfterTransaction(HypernateContext ctx, Object result) {
      afterHookCalled = true;
      afterResult = result;
      callOrder.add("onAfterTransaction");
    }
  }
}
