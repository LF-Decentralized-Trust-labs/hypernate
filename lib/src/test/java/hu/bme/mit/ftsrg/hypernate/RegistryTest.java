/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;
import hu.bme.mit.ftsrg.hypernate.registry.EntityExistsException;
import hu.bme.mit.ftsrg.hypernate.registry.EntityNotFoundException;
import hu.bme.mit.ftsrg.hypernate.registry.MissingPrimaryKeysException;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import hu.bme.mit.ftsrg.hypernate.registry.SerializationException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import lombok.experimental.FieldNameConstants;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
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
class RegistryTest {

  private static final TestEntity entity = new TestEntity("fooValue", 110);

  private static final CompositeKey ENTITY_COMPOSITE_KEY =
      new CompositeKey(entity.getClass().getName(), entity.foo, entity.bar.toString());
  private static final String ENTITY_COMPOSITE_KEY_STR = ENTITY_COMPOSITE_KEY.toString();
  private static final byte[] ENTITY_BUFFER;

  static {
    try {
      ENTITY_BUFFER = JSON.serialize(entity).getBytes(StandardCharsets.UTF_8);
    } catch (SerializationException e) {
      throw new RuntimeException(e);
    }
  }

  @Mock private ChaincodeStub stub;

  private Registry registry;

  @BeforeEach
  void setup() {
    registry = new Registry(stub);
  }

  @Test
  void given_entity_without_primary_keys_when_doing_anything_then_throws_exception() {
    record KeylessTestEntity(String foo, Integer bar) {}
    final KeylessTestEntity keylessEntity = new KeylessTestEntity("fooValue", 110);

    assertThrows(MissingPrimaryKeysException.class, () -> registry.mustCreate(keylessEntity));
    assertThrows(MissingPrimaryKeysException.class, () -> registry.tryCreate(keylessEntity));
    assertThrows(MissingPrimaryKeysException.class, () -> registry.mustUpdate(keylessEntity));
    assertThrows(MissingPrimaryKeysException.class, () -> registry.tryUpdate(keylessEntity));
    assertThrows(MissingPrimaryKeysException.class, () -> registry.mustDelete(keylessEntity));
    assertThrows(MissingPrimaryKeysException.class, () -> registry.tryDelete(keylessEntity));
    assertThrows(
        MissingPrimaryKeysException.class,
        () -> registry.mustRead(KeylessTestEntity.class, keylessEntity.foo));
    assertThrows(
        MissingPrimaryKeysException.class,
        () -> registry.tryRead(KeylessTestEntity.class, keylessEntity.foo));
  }

  @FieldNameConstants
  @PrimaryKey({
    @AttributeInfo(name = TestEntity.Fields.foo),
    @AttributeInfo(name = TestEntity.Fields.bar)
  })
  private record TestEntity(String foo, Integer bar) {}

  @Nested
  class given_empty_ledger {

    @Test
    void when_must_create_then_call_putState()
        throws SerializationException, EntityExistsException {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(new byte[] {});

      registry.mustCreate(entity);

      then(stub).should().putState(eq(ENTITY_COMPOSITE_KEY_STR), any(byte[].class));
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_try_create_then_return_true_and_call_putState() throws SerializationException {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(new byte[] {});

      boolean result = registry.tryCreate(entity);

      assertTrue(result);
      then(stub).should().putState(eq(ENTITY_COMPOSITE_KEY_STR), any(byte[].class));
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_must_update_then_throw_not_found() {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(new byte[] {});

      assertThrows(EntityNotFoundException.class, () -> registry.mustUpdate(entity));
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_try_update_then_return_false_and_do_nothing() {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(new byte[] {});

      AtomicBoolean result = new AtomicBoolean(false);
      assertDoesNotThrow(() -> result.set(registry.tryUpdate(entity)));
      assertFalse(result.get());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_must_delete_then_throw_not_found() {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(new byte[] {});

      assertThrows(EntityNotFoundException.class, () -> registry.mustDelete(entity));
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_try_delete_then_return_false_and_do_nothing() {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(new byte[] {});

      AtomicBoolean result = new AtomicBoolean(false);
      assertDoesNotThrow(() -> result.set(registry.tryDelete(entity)));
      assertFalse(result.get());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_readAll_then_return_empty_list() throws SerializationException {
      given(stub.createCompositeKey(anyString())).willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getStateByPartialCompositeKey(anyString()))
          .willReturn(
              new QueryResultsIterator<>() {
                @Override
                public void close() {}

                @Override
                public @Nonnull Iterator<KeyValue> iterator() {
                  return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                      return false;
                    }

                    @Override
                    public KeyValue next() {
                      throw new UnsupportedOperationException();
                    }
                  };
                }
              });

      List<TestEntity> results = registry.readAll(TestEntity.class);

      then(stub).should().getStateByPartialCompositeKey(anyString());
      assertTrue(results.isEmpty());
      verifyNoMoreInteractions(stub);
    }

    @Nested
    class when_must_read {

      @Test
      void with_insufficient_key_parts_then_throw_illegal_argument() {
        assertThrows(
            IllegalArgumentException.class, () -> registry.mustRead(TestEntity.class, entity.foo));
        verifyNoMoreInteractions(stub);
      }

      @Test
      void with_complete_key_then_throw_not_found() {
        given(stub.createCompositeKey(anyString(), any(String[].class)))
            .willReturn(ENTITY_COMPOSITE_KEY);
        given(stub.getState(anyString())).willReturn(new byte[] {});

        assertThrows(
            EntityNotFoundException.class,
            () -> registry.mustRead(TestEntity.class, entity.foo, entity.bar));
        verifyNoMoreInteractions(stub);
      }
    }

    @Nested
    class when_try_read {

      @Test
      void with_insufficient_key_parts_then_throw_illegal_argument() {
        assertThrows(
            IllegalArgumentException.class, () -> registry.tryRead(TestEntity.class, entity.foo));
        verifyNoMoreInteractions(stub);
      }

      @Test
      void with_complete_key_then_return_null() throws SerializationException {
        given(stub.createCompositeKey(anyString(), any(String[].class)))
            .willReturn(ENTITY_COMPOSITE_KEY);
        given(stub.getState(anyString())).willReturn(new byte[] {});

        TestEntity readEntity = registry.tryRead(TestEntity.class, entity.foo, entity.bar);

        assertNull(readEntity);
        verifyNoMoreInteractions(stub);
      }
    }
  }

  @Nested
  class given_existing_entity {

    @Test
    void when_must_create_then_throw_exists() {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

      assertThrows(EntityExistsException.class, () -> registry.mustCreate(entity));
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_try_create_then_return_false_and_do_nothing() {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

      AtomicBoolean result = new AtomicBoolean(false);
      assertDoesNotThrow(() -> result.set(registry.tryCreate(entity)));
      assertFalse(result.get());
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_must_update_then_call_putState()
        throws SerializationException, EntityNotFoundException {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

      registry.mustUpdate(entity);

      then(stub).should().putState(eq(ENTITY_COMPOSITE_KEY_STR), any(byte[].class));
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_try_update_then_return_true_and_call_putState() throws SerializationException {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

      boolean result = registry.tryUpdate(entity);

      assertTrue(result);
      then(stub).should().putState(eq(ENTITY_COMPOSITE_KEY_STR), any(byte[].class));
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_must_delete_then_call_delState() throws EntityNotFoundException {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

      registry.mustDelete(entity);

      then(stub).should().delState(ENTITY_COMPOSITE_KEY_STR);
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_try_delete_then_return_true_and_call_delState() throws EntityNotFoundException {
      given(stub.createCompositeKey(anyString(), any(String[].class)))
          .willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

      boolean result = registry.tryDelete(entity);

      assertTrue(result);
      then(stub).should().delState(ENTITY_COMPOSITE_KEY_STR);
      verifyNoMoreInteractions(stub);
    }

    @Test
    void when_readAll_then_return_one_long_list() throws SerializationException {
      given(stub.createCompositeKey(anyString())).willReturn(ENTITY_COMPOSITE_KEY);
      given(stub.getStateByPartialCompositeKey(anyString()))
          .willReturn(
              new QueryResultsIterator<>() {
                private boolean done = false;

                @Override
                public void close() {}

                @Override
                public @Nonnull Iterator<KeyValue> iterator() {
                  return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                      return !done;
                    }

                    @Override
                    public KeyValue next() {
                      if (done) {
                        throw new UnsupportedOperationException();
                      }

                      done = true;

                      return new KeyValue() {
                        @Override
                        public String getKey() {
                          return ENTITY_COMPOSITE_KEY_STR;
                        }

                        @Override
                        public byte[] getValue() {
                          return ENTITY_BUFFER;
                        }

                        @Override
                        public String getStringValue() {
                          return Arrays.toString(getValue());
                        }
                      };
                    }
                  };
                }
              });

      List<TestEntity> results = registry.readAll(TestEntity.class);

      then(stub).should().getStateByPartialCompositeKey(anyString());
      assertEquals(1, results.size());
      verifyNoMoreInteractions(stub);
    }

    @Nested
    class when_must_read {

      @Test
      void with_insufficient_key_parts_then_throw_illegal_argument() {
        assertThrows(
            IllegalArgumentException.class, () -> registry.mustRead(TestEntity.class, entity.foo));
        verifyNoMoreInteractions(stub);
      }

      @Test
      void with_complete_key_then_call_getState_and_return_entity()
          throws SerializationException, EntityNotFoundException {
        given(stub.createCompositeKey(anyString(), any(String[].class)))
            .willReturn(ENTITY_COMPOSITE_KEY);
        given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

        TestEntity result = registry.mustRead(TestEntity.class, entity.foo, entity.bar);

        then(stub).should().getState(ENTITY_COMPOSITE_KEY_STR);
        assertEquals(entity, result);
        verifyNoMoreInteractions(stub);
      }
    }

    @Nested
    class when_try_read {

      @Test
      void with_insufficient_key_parts_then_throw_illegal_argument() {
        assertThrows(
            IllegalArgumentException.class, () -> registry.tryRead(TestEntity.class, entity.foo));
        verifyNoMoreInteractions(stub);
      }

      @Test
      void with_complete_key_then_call_getState_and_return_entity() throws SerializationException {
        given(stub.createCompositeKey(anyString(), any(String[].class)))
            .willReturn(ENTITY_COMPOSITE_KEY);
        given(stub.getState(anyString())).willReturn(ENTITY_BUFFER);

        TestEntity result = registry.tryRead(TestEntity.class, entity.foo, entity.bar);

        then(stub).should().getState(ENTITY_COMPOSITE_KEY_STR);
        assertEquals(entity, result);
        verifyNoMoreInteractions(stub);
      }
    }
  }
}
