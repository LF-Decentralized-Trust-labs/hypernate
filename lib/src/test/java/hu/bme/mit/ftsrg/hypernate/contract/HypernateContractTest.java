/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.contract;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.middleware.LoggingStubMiddleware;
import hu.bme.mit.ftsrg.hypernate.middleware.MiddlewareInfo;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.protos.msp.SerializedIdentity;
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

  private static final String VALID_PEM_CERT =
      "-----BEGIN CERTIFICATE-----\n"
          + "MIICwjCCAaqgAwIBAgIJAMrYnN0M2eFRMA0GCSqGSIb3DQEBCwUAMA8xDTALBgNV\n"
          + "BAMTBFRlc3QwHhcNMjYwODAxMTIyOTAwWhcNMjcwODAxMTIyOTAwWjAPMQ0wCwYD\n"
          + "VQQDEwRUZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtCAToQs/\n"
          + "nstEPd9ipdrXpf4isUwQ+Dqe49UlVhrETxLJHwwLJzJx6xHfLN7KGk6CO6Uu4chO\n"
          + "b94fgTRL5OCMi+Qbsl+OzZ1yisxk2Z4phwjYxvsxAfkhtDuj1UQyhE82lFvWd/xM\n"
          + "3v+nuu+R+NbPXxxHtKE2wXj/BYXOWV398UcGPaSBLHlcpwUOFruRuSebynuLrroZ\n"
          + "mmr/Xr3iT3ZZXzj/JYx/8mx5U3HKeomXU4JwrTj+2Z60mHXjkpbWTI8m2LBFdcbY\n"
          + "getFvtQuhAI/4kZ9SzzsSrM/4SddBduq+Dg5ocuSI+pzgIVd1/K62z9t19HThmAx\n"
          + "7WJLlocS9Z1jnwIDAQABoyEwHzAdBgNVHQ4EFgQUmtHU9mNATTWSvGEU7lRCZ+w2\n"
          + "YyIwDQYJKoZIhvcNAQELBQADggEBACMfRnwsoF4TSpf/ay72TwCfBG4sVFFSgcUN\n"
          + "cCy+vB84GuVRJb4Fp6ivieVMgIu1N60zsS5DvIPDT2Pp9tFenz9qVSpSFE2kgyZH\n"
          + "3c2GnFtp1APgE/fvgVtVY3N7W18y9jnO3ieKSr3Us4JcHuwdqcT8b+qLNLYF7MgV\n"
          + "tFz7EM7OF2N1lrcfxqNThRft2wQKOjfDriL0Ddw5CwEWc01OdZCBx2qe7AkU72lj\n"
          + "utBbI/3N5FqK+UMrzSJcTODQ/ngjYrcqmoiYDbAu3kG4hJVCjVKVoRcFv1Faqr38\n"
          + "BmkRUYLGH8oLZiLM03lcjHuFRnseKVXgw6/mlQn23W7bxYE0+P4=\n"
          + "-----END CERTIFICATE-----\n";

  @Mock(lenient = true)
  private ChaincodeStub fabricStub;

  @MiddlewareInfo({LoggingStubMiddleware.class})
  private static class DummyContract implements HypernateContract {}

  private DummyContract contract;

  @BeforeEach
  void setUp() {
    contract = new DummyContract();
    byte[] validIdentityBytes =
        SerializedIdentity.newBuilder()
            .setMspid("Org1MSP")
            .setIdBytes(ByteString.copyFromUtf8(VALID_PEM_CERT))
            .build()
            .toByteArray();
    given(fabricStub.getCreator()).willReturn(validIdentityBytes);
  }

  @Test
  void when_createContext_then_returns_HypernateContext_with_middleware_chain() {
    Context ctx = contract.createContext(fabricStub);

    assertInstanceOf(HypernateContext.class, ctx);
    HypernateContext hypCtx = (HypernateContext) ctx;

    assertEquals(fabricStub, hypCtx.getFabricStub());
    assertEquals(1, hypCtx.getMiddlewareChain().middlewares().size());
  }

  @Test
  void when_before_and_after_transaction_then_executes_without_exceptions() {
    Context ctx = contract.createContext(fabricStub);

    assertDoesNotThrow(() -> contract.beforeTransaction(ctx));
    assertDoesNotThrow(() -> contract.afterTransaction(ctx, "result"));
  }
}
