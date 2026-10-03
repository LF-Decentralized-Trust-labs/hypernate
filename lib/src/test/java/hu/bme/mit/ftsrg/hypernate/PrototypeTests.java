/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.bme.mit.ftsrg.hypernate.context.CrossChaincodeException;
import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.endorsement.EndorsementPolicy;
import hu.bme.mit.ftsrg.hypernate.endorsement.InvalidEndorsementPolicyException;
import hu.bme.mit.ftsrg.hypernate.middleware.StubMiddlewareChain;
import hu.bme.mit.ftsrg.hypernate.middleware.WriteBackCachedStubMiddleware;
import hu.bme.mit.ftsrg.hypernate.registry.EntityNotFoundException;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import hu.bme.mit.ftsrg.hypernate.registry.SortOrder;
import hu.bme.mit.ftsrg.hypernate.registry.query.InvalidRangeQueryException;
import hu.bme.mit.ftsrg.hypernate.registry.query.UncommittedStateException;
import hu.bme.mit.ftsrg.hypernate.sample.Asset;
import hu.bme.mit.ftsrg.hypernate.sample.AssetStatus;
import hu.bme.mit.ftsrg.hypernate.sample.PricingResponse;
import hu.bme.mit.ftsrg.hypernate.sample.SampleChaincode;
import hu.bme.mit.ftsrg.hypernate.sample.SensitiveAsset;
import hu.bme.mit.ftsrg.hypernate.sample.TransferResult;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.util.Collections;
import java.util.List;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
public class PrototypeTests {

  @Test
  public void testQueryBuilderSelector_SingleCondition() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry.query(Asset.class).where("color").is("blue").execute();

    String json = queryCaptor.getValue();
    assertThat(json).contains("\"color\":\"blue\"");
    assertThat(json).contains("\"docType\":\"HU.BME.MIT.FTSRG.HYPERNATE.SAMPLE.ASSET\"");
  }

  @Test
  public void testQueryBuilderSelector_MultiCondition() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry.query(Asset.class).where("color").is("blue").and("size").greaterThan(10).execute();

    String json = queryCaptor.getValue();
    assertThat(json).contains("\"color\":\"blue\"");
    assertThat(json).contains("\"size\":{\"$gt\":10}");
  }

  @Test
  public void testQueryBuilderSelector_Between() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry.query(Asset.class).where("value").between(100, 500).execute();

    String json = queryCaptor.getValue();
    assertThat(json).contains("\"$gte\":100");
    assertThat(json).contains("\"$lte\":500");
  }

  @Test
  public void testQueryBuilderSelector_In() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry.query(Asset.class).and("owner").in("Alice", "Bob").execute();
    assertThat(queryCaptor.getValue()).contains("\"$in\":[\"Alice\",\"Bob\"]");
  }

  @Test
  public void testQueryBuilderSelector_SortBy() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry
        .query(Asset.class)
        .sortBy("value", SortOrder.DESC)
        .sortBy("color", SortOrder.ASC)
        .execute();
    assertThat(queryCaptor.getValue())
        .contains("\"sort\":[{\"value\":\"desc\"},{\"color\":\"asc\"}]");
  }

  @Test
  public void testQueryBuilderSelector_UseIndex() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry.query(Asset.class).useIndex("designDocName", "indexName").execute();
    assertThat(queryCaptor.getValue()).contains("\"use_index\":[\"designDocName\",\"indexName\"]");
  }

  @Test
  public void testQueryBuilderSelector_LimitSkip() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry.query(Asset.class).limit(50).skip(10).execute();
    String json = queryCaptor.getValue();
    assertThat(json).contains("\"limit\":50");
    assertThat(json).contains("\"skip\":10");
  }

  @Test
  public void testQueryBuilderSelector_DocTypeAutoFilter() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(queryCaptor.capture())).thenReturn(iter);

    registry.query(Asset.class).execute();
    assertThat(queryCaptor.getValue())
        .contains("\"docType\":\"HU.BME.MIT.FTSRG.HYPERNATE.SAMPLE.ASSET\"");
  }

  @Test
  public void testUncommittedStateException_ThrowsWhenDirty() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    StubMiddlewareChain chain =
        StubMiddlewareChain.builder(fabricStub).push(WriteBackCachedStubMiddleware.class).build();
    WriteBackCachedStubMiddleware mw = (WriteBackCachedStubMiddleware) chain.getFirst();
    mw.putState("k", "v".getBytes());

    Registry registry = new Registry(chain.getFirst());
    assertThatThrownBy(() -> registry.query(Asset.class).where("color").is("x").execute())
        .isInstanceOf(UncommittedStateException.class)
        .hasMessageContaining("uncommitted writes exist in the middleware cache")
        .hasMessageContaining("invisible to getQueryResult")
        .hasMessageContaining("TransactionEnd");
  }

  @Test
  public void testUncommittedStateException_CleanCache() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    StubMiddlewareChain chain =
        StubMiddlewareChain.builder(fabricStub).push(WriteBackCachedStubMiddleware.class).build();
    Registry registry = new Registry(chain.getFirst());

    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(anyString())).thenReturn(iter);

    registry.query(Asset.class).where("color").is("x").execute();
  }

  @Test
  public void testUncommittedStateException_NoMiddleware() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);

    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getQueryResult(anyString())).thenReturn(iter);

    registry.query(Asset.class).where("color").is("x").execute();
  }

  public static class NoPKEntity {
    public String id;
  }

  @Test
  public void testRangeQueryGuard_NoPK() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    assertThatThrownBy(() -> registry.rangeQuery(NoPKEntity.class).withKeyPrefix("x"))
        .isInstanceOf(InvalidRangeQueryException.class)
        .hasMessageContaining("no @PrimaryKey annotation found");
  }

  @Test
  public void testRangeQueryGuard_TooManyPrefixParts() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    assertThatThrownBy(() -> registry.rangeQuery(Asset.class).withKeyPrefix("a", "b", "c"))
        .isInstanceOf(InvalidRangeQueryException.class)
        .hasMessageContaining("Too many prefix parts");
  }

  @Test
  public void testRangeQueryGuard_NullPrefix() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    assertThatThrownBy(() -> registry.rangeQuery(Asset.class).withKeyPrefix("Alice", null))
        .isInstanceOf(InvalidRangeQueryException.class)
        .hasMessageContaining("Null prefix component at index 1");
  }

  @Test
  public void testRangeQueryGuard_FullScan() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.createCompositeKey(anyString(), any(String[].class)))
        .thenReturn(new CompositeKey("test"));
    Registry registry = new Registry(fabricStub);

    QueryResultsIterator<KeyValue> iter = mock(QueryResultsIterator.class);
    when(iter.iterator()).thenReturn(Collections.emptyIterator());
    when(fabricStub.getStateByPartialCompositeKey(any(CompositeKey.class))).thenReturn(iter);

    List<Asset> result = registry.rangeQuery(Asset.class).fullScan().execute();
    assertThat(result).isEmpty();
  }

  @Test
  public void testRangeQueryGuard_ExecuteWithoutConfig() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    Registry registry = new Registry(fabricStub);
    assertThatThrownBy(() -> registry.rangeQuery(Asset.class).execute())
        .isInstanceOf(InvalidRangeQueryException.class)
        .hasMessageContaining("Configure the query before executing");
  }

  @Test
  public void testCrossChaincode_Success() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();

    Response resp = mock(Response.class);
    when(resp.getStatus()).thenReturn(Response.Status.SUCCESS);
    when(resp.getStringPayload()).thenReturn(JSON.serialize(new TransferResult(true, "OK", 100)));
    when(fabricStub.invokeChaincode(eq("cc"), org.mockito.ArgumentMatchers.<List<byte[]>>any()))
        .thenReturn(resp);

    TransferResult res = ctx.invoke("cc", "fn").returning(TransferResult.class).execute();
    assertThat(res.approved()).isTrue();
  }

  @Test
  public void testCrossChaincode_ErrorPropagation() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();

    Response resp = mock(Response.class);
    when(resp.getStatus()).thenReturn(Response.Status.INTERNAL_SERVER_ERROR);
    when(resp.getMessage()).thenReturn("chaincode panicked");
    when(fabricStub.invokeChaincode(eq("testCC"), org.mockito.ArgumentMatchers.<List<byte[]>>any()))
        .thenReturn(resp);

    assertThatThrownBy(() -> ctx.invoke("testCC", "doWork").returningVoid().execute())
        .isInstanceOf(CrossChaincodeException.class)
        .satisfies(
            e -> {
              CrossChaincodeException ex = (CrossChaincodeException) e;
              assertThat(ex.getChaincodeId()).isEqualTo("testCC");
              assertThat(ex.getFunctionName()).isEqualTo("doWork");
              assertThat(ex.getStatusCode()).isEqualTo(500);
              assertThat(ex.getOriginalMessage()).isEqualTo("chaincode panicked");
            });
  }

  @Test
  public void testCrossChaincode_ReturningVoid() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();

    Response resp = mock(Response.class);
    when(resp.getStatus()).thenReturn(Response.Status.SUCCESS);
    when(fabricStub.invokeChaincode(eq("cc"), org.mockito.ArgumentMatchers.<List<byte[]>>any()))
        .thenReturn(resp);

    Void res = ctx.invoke("cc", "fn").returningVoid().execute();
    assertThat(res).isNull();
    verify(resp, never()).getStringPayload();
  }

  @Test
  public void testCrossChaincode_NoReturnConfig() {
    HypernateContext ctx = mock(HypernateContext.class);
    ChaincodeStub dummyStub = mock(ChaincodeStub.class);
    when(ctx.getStub()).thenReturn(dummyStub);
    when(ctx.getFabricStub()).thenReturn(dummyStub);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();
    assertThatThrownBy(() -> ctx.invoke("cc", "fn").execute())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("returning(Class) or .returningVoid()");
  }

  @Test
  public void testCrossChaincode_MixedArgs() {
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();
    assertThatThrownBy(
            () ->
                ctx.invoke("cc", "fn")
                    .withArgs("x")
                    .withRawArgs("y".getBytes())
                    .returningVoid()
                    .execute())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testEndorsementPolicyValidation_Valid() {
    EndorsementPolicy.of("OR('Org1MSP.peer', 'Org2MSP.peer')");
    EndorsementPolicy.of("AND('Org1MSP.member', 'Org2MSP.member')");
    EndorsementPolicy.of("OutOf(2, 'Org1MSP.peer', 'Org2MSP.peer', 'Org3MSP.peer')");
  }

  @Test
  public void testEndorsementPolicyValidation_Unbalanced1() {
    assertThatThrownBy(() -> EndorsementPolicy.of("OR('Org1MSP.peer'"))
        .isInstanceOf(InvalidEndorsementPolicyException.class)
        .hasMessageContaining("Unclosed parenthesis");
  }

  @Test
  public void testEndorsementPolicyValidation_Unbalanced2() {
    assertThatThrownBy(() -> EndorsementPolicy.of(")OR('Org1MSP.peer')"))
        .isInstanceOf(InvalidEndorsementPolicyException.class)
        .hasMessageContaining("Unexpected closing parenthesis");
  }

  @Test
  public void testEndorsementPolicyValidation_UnsupportedFunction() {
    assertThatThrownBy(() -> EndorsementPolicy.of("XOR('Org1MSP.peer')"))
        .isInstanceOf(InvalidEndorsementPolicyException.class)
        .hasMessageContaining("Unsupported policy function 'XOR'");
  }

  @Test
  public void testEndorsementPolicyValidation_InvalidMSPFormat() {
    assertThatThrownBy(() -> EndorsementPolicy.of("OR('Org1.peer')"))
        .isInstanceOf(InvalidEndorsementPolicyException.class)
        .hasMessageContaining("Invalid MSP principal");
  }

  @Test
  public void testEndorsementPolicyValidation_InvalidRole() {
    assertThatThrownBy(() -> EndorsementPolicy.of("OR('Org1MSP.validator')"))
        .isInstanceOf(InvalidEndorsementPolicyException.class)
        .hasMessageContaining("Invalid MSP principal");
  }

  @Test
  public void testEndorsementPolicyValidation_Empty() {
    assertThatThrownBy(() -> EndorsementPolicy.of(""))
        .isInstanceOf(InvalidEndorsementPolicyException.class);
  }

  @Test
  public void testEndorsementPolicyValidation_OutOfArg() {
    assertThatThrownBy(() -> EndorsementPolicy.of("OutOf(two, 'Org1MSP.peer')"))
        .isInstanceOf(InvalidEndorsementPolicyException.class)
        .hasMessageContaining("integer as its first argument");
  }

  @Test
  public void testEndorsementPolicyBuilder_AND() {
    EndorsementPolicy built =
        EndorsementPolicy.builder().and().org("Org1").peer().org("Org2").peer().build();
    assertThat(built.getExpression()).isEqualTo("AND('Org1MSP.peer', 'Org2MSP.peer')");
  }

  @Test
  public void testEndorsementPolicyBuilder_OutOf() {
    EndorsementPolicy built =
        EndorsementPolicy.builder()
            .outOf(2)
            .org("Org1")
            .peer()
            .org("Org2")
            .peer()
            .org("Org3")
            .peer()
            .build();
    assertThat(built.getExpression())
        .isEqualTo("OutOf(2, 'Org1MSP.peer', 'Org2MSP.peer', 'Org3MSP.peer')");
  }

  @Test
  public void testEndorsementPolicyBuilder_EqualsDirect() {
    String expr = "OR('Org1MSP.peer', 'Org2MSP.peer')";
    EndorsementPolicy direct = EndorsementPolicy.of(expr);
    EndorsementPolicy built =
        EndorsementPolicy.builder().or().org("Org1").peer().org("Org2").peer().build();
    assertThat(direct.getExpression()).isEqualTo(built.getExpression());
  }

  @Test
  public void testEndorsementPolicyBuilder_Empty() {
    assertThatThrownBy(() -> EndorsementPolicy.builder().build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testEndorsementPolicyBuilder_ExceedsThreshold() {
    assertThatThrownBy(() -> EndorsementPolicy.builder().outOf(3).org("Org1").peer().build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exceeds number of principals");
  }

  @Test
  public void testRegistryEndorsement_mustCreateSensitive() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.createCompositeKey(anyString(), any(String[].class)))
        .thenReturn(new CompositeKey("mock"));
    when(fabricStub.getState(anyString())).thenReturn(null);
    Registry registry = new Registry(fabricStub);

    SensitiveAsset sensitiveAsset =
        new SensitiveAsset(
            "id", "red", 1, "owner", 10, 0, AssetStatus.ACTIVE, "A", Collections.emptyList());
    registry.mustCreate(sensitiveAsset);

    verify(fabricStub).setStateValidationParameter(anyString(), any(byte[].class));
  }

  @Test
  public void testRegistryEndorsement_mustCreateNormal() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.createCompositeKey(anyString(), any(String[].class)))
        .thenReturn(new CompositeKey("mock"));
    when(fabricStub.getState(anyString())).thenReturn(null);
    Registry registry = new Registry(fabricStub);

    Asset asset = new Asset("id", "red", 1, "owner", 10, 0, AssetStatus.ACTIVE);
    registry.mustCreate(asset);

    verify(fabricStub, never()).setStateValidationParameter(anyString(), any(byte[].class));
  }

  @Test
  public void testRegistryEndorsement_setEndorsementPolicyNotFound() {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.createCompositeKey(anyString(), any(String[].class)))
        .thenReturn(new CompositeKey("mock"));
    when(fabricStub.getState(anyString())).thenReturn(new byte[0]);
    Registry registry = new Registry(fabricStub);

    Asset asset = new Asset("id", "red", 1, "owner", 10);
    EndorsementPolicy pol = EndorsementPolicy.of("OR('Org1MSP.peer')");

    assertThatThrownBy(() -> registry.setEndorsementPolicy(asset, pol))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("Cannot set endorsement policy: entity does not exist");
  }

  @Test
  public void testSampleChaincode_createAssetBlankId() {
    SampleChaincode sc = new SampleChaincode();
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    org.hyperledger.fabric.shim.ext.sbe.StateBasedEndorsement sbe =
        mock(org.hyperledger.fabric.shim.ext.sbe.StateBasedEndorsement.class);
    when(fabricStub.getTxTimestamp()).thenReturn(java.time.Instant.now());
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.getRegistry()).thenReturn(new Registry(fabricStub));
    assertThatThrownBy(() -> sc.createAsset(ctx, "", "red", 10, "Alice", 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  public void testSampleChaincode_createAssetNegativeSize() {
    SampleChaincode sc = new SampleChaincode();
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.getTxTimestamp()).thenReturn(java.time.Instant.now());
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.getRegistry()).thenReturn(new Registry(fabricStub));
    assertThatThrownBy(() -> sc.createAsset(ctx, "a1", "red", -1, "Alice", 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size");
  }

  @Test
  public void testSampleChaincode_initiateTransferArchived() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.createCompositeKey(anyString(), any(String[].class)))
        .thenReturn(new CompositeKey("mock"));
    Asset archived = new Asset("id", "red", 1, "owner", 10, 0, AssetStatus.ARCHIVED);
    when(fabricStub.getState(anyString())).thenReturn(JSON.serialize(archived).getBytes());

    SampleChaincode sc = new SampleChaincode();
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getRegistry()).thenReturn(new Registry(fabricStub));
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();

    assertThatThrownBy(() -> sc.initiateTransfer(ctx, "id", "owner", "new", 10))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("not in ACTIVE status");
  }

  @Test
  public void testSampleChaincode_initiateTransferRejected() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.createCompositeKey(anyString(), any(String[].class)))
        .thenReturn(new CompositeKey("mock"));
    Asset active = new Asset("id", "red", 1, "owner", 10, 0, AssetStatus.ACTIVE);
    when(fabricStub.getState(anyString())).thenReturn(JSON.serialize(active).getBytes());

    Response resp = mock(Response.class);
    when(resp.getStatus()).thenReturn(Response.Status.SUCCESS);
    when(resp.getStringPayload())
        .thenReturn(JSON.serialize(new PricingResponse(false, 0, "Too low")));
    when(fabricStub.invokeChaincode(
            eq("PricingChaincode"), org.mockito.ArgumentMatchers.<List<byte[]>>any()))
        .thenReturn(resp);

    SampleChaincode sc = new SampleChaincode();
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getRegistry()).thenReturn(new Registry(fabricStub));
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();

    assertThatThrownBy(() -> sc.initiateTransfer(ctx, "id", "owner", "new", 10))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Transfer rejected");
  }

  @Test
  public void testSampleChaincode_initiateTransferSuccess() throws Exception {
    ChaincodeStub fabricStub = mock(ChaincodeStub.class);
    when(fabricStub.createCompositeKey(anyString(), any(String[].class)))
        .thenReturn(new CompositeKey("mock"));
    Asset active = new Asset("id", "red", 1, "owner", 10, 0, AssetStatus.ACTIVE);
    when(fabricStub.getState(anyString())).thenReturn(JSON.serialize(active).getBytes());

    Response resp = mock(Response.class);
    when(resp.getStatus()).thenReturn(Response.Status.SUCCESS);
    when(resp.getStringPayload()).thenReturn(JSON.serialize(new PricingResponse(true, 50, "OK")));
    when(fabricStub.invokeChaincode(
            eq("PricingChaincode"), org.mockito.ArgumentMatchers.<List<byte[]>>any()))
        .thenReturn(resp);

    SampleChaincode sc = new SampleChaincode();
    HypernateContext ctx = mock(HypernateContext.class);
    when(ctx.getRegistry()).thenReturn(new Registry(fabricStub));
    when(ctx.getStub()).thenReturn(fabricStub);
    when(ctx.getFabricStub()).thenReturn(fabricStub);
    when(ctx.invoke(anyString(), anyString())).thenCallRealMethod();

    Asset result = sc.initiateTransfer(ctx, "id", "owner", "new", 50);
    assertThat(result.getStatus()).isEqualTo(AssetStatus.PENDING_TRANSFER);
    verify(fabricStub).putState(anyString(), any(byte[].class)); // Must update
  }
}
