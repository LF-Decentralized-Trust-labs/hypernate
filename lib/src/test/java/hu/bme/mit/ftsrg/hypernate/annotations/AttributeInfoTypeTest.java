/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import hu.bme.mit.ftsrg.hypernate.mappers.IntegerZeroPadder;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttributeInfoTypeTest {

  @Mock private ChaincodeStub stub;
  private Registry registry;

  @BeforeEach
  void setup() {
    registry = new Registry(stub);
  }

  @PrimaryKey({@AttributeInfo(name = "id")})
  record LegacyEntity(String id) {}

  @Test
  void legacyCompatibility() {
    given(stub.createCompositeKey(anyString(), any(String[].class)))
        .willReturn(new CompositeKey("fake"));
    given(stub.getState(anyString())).willReturn(new byte[] {});
    assertTrue(registry.tryCreate(new LegacyEntity("test")));
  }

  @PrimaryKey({@AttributeInfo(name = "val", type = Integer.class, mapper = IntegerZeroPadder.class)})
  record ValidEntity(Integer val) {}

  @Test
  void validExplicitMapper() {
    given(stub.createCompositeKey(anyString(), any(String[].class)))
        .willReturn(new CompositeKey("fake"));
    given(stub.getState(anyString())).willReturn(new byte[] {});
    assertTrue(registry.tryCreate(new ValidEntity(42)));
  }

  @PrimaryKey({@AttributeInfo(name = "val", type = String.class, mapper = IntegerZeroPadder.class)})
  record InvalidEntity(String val) {}

  @Test
  void invalidExplicitMapper() {
    assertThrows(IllegalArgumentException.class, () -> registry.tryCreate(new InvalidEntity("42")));
  }

  @PrimaryKey({@AttributeInfo(name = "val", type = Integer.class)})
  record InferredEntity(Integer val) {}

  @Test
  void defaultMapperInference() {
    given(stub.createCompositeKey(anyString(), any(String[].class)))
        .willAnswer(inv -> {
            String[] args = inv.getArgument(1);
            if (args[0].length() == 10) { 
                return new CompositeKey("fake");
            }
            throw new RuntimeException("Inference failed, expected padded key, got: " + args[0]);
        });
    given(stub.getState(anyString())).willReturn(new byte[] {});
    assertTrue(registry.tryCreate(new InferredEntity(42)));
  }
}
