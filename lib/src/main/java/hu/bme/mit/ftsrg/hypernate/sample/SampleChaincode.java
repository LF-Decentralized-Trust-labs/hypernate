/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.sample;

import hu.bme.mit.ftsrg.hypernate.context.HypernateContext;
import hu.bme.mit.ftsrg.hypernate.endorsement.EndorsementPolicy;
import hu.bme.mit.ftsrg.hypernate.registry.Registry;
import hu.bme.mit.ftsrg.hypernate.registry.SortOrder;
import hu.bme.mit.ftsrg.hypernate.registry.query.UncommittedStateException;
import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.util.List;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.Transaction;

@Contract(
    name = "SampleChaincode",
    info =
        @Info(
            title = "SampleChaincode",
            description = "A sample Hypernate chaincode",
            version = "1.0"))
@Default
public class SampleChaincode implements ContractInterface {

  @Transaction
  public void createAsset(
      HypernateContext ctx, String id, String color, int size, String owner, int value) {
    if (id == null || id.isBlank())
      throw new IllegalArgumentException("Field 'id' must not be blank");
    if (color == null || color.isBlank())
      throw new IllegalArgumentException("Field 'color' must not be blank");
    if (owner == null || owner.isBlank())
      throw new IllegalArgumentException("Field 'owner' must not be blank");
    if (size <= 0) throw new IllegalArgumentException("Field 'size' must be positive");
    if (value <= 0) throw new IllegalArgumentException("Field 'value' must be positive");

    long timestampMs = ctx.getStub().getTxTimestamp().toEpochMilli();
    Asset asset = new Asset(id, color, size, owner, value, timestampMs, AssetStatus.ACTIVE);
    ctx.getRegistry().mustCreate(asset);
  }

  @Transaction
  public void createSensitiveAsset(
      HypernateContext ctx,
      String id,
      String color,
      int size,
      String owner,
      int value,
      String classLevel,
      String orgsJson) {
    if (id == null || id.isBlank())
      throw new IllegalArgumentException("Field 'id' must not be blank");
    if (color == null || color.isBlank())
      throw new IllegalArgumentException("Field 'color' must not be blank");
    if (owner == null || owner.isBlank())
      throw new IllegalArgumentException("Field 'owner' must not be blank");
    if (size <= 0) throw new IllegalArgumentException("Field 'size' must be positive");
    if (value <= 0) throw new IllegalArgumentException("Field 'value' must be positive");

    List<String> orgs;
    try {
      @SuppressWarnings("unchecked")
      List<String> parsed = JSON.deserialize(orgsJson, List.class);
      orgs = parsed;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to deserialize orgsJson: " + e.getMessage(), e);
    }

    long timestampMs = ctx.getStub().getTxTimestamp().toEpochMilli();

    SensitiveAsset sensitiveAsset =
        new SensitiveAsset(
            id, color, size, owner, value, timestampMs, AssetStatus.ACTIVE, classLevel, orgs);
    ctx.getRegistry().mustCreate(sensitiveAsset);
  }

  @Transaction
  public Asset getAsset(HypernateContext ctx, String owner, String id) {
    return ctx.getRegistry().mustRead(Asset.class, owner, id);
  }

  @Transaction
  public List<Asset> queryAssetsByColorAndOwner(
      HypernateContext ctx, String color, String owner1, String owner2) {
    try {
      return ctx.getRegistry()
          .query(Asset.class)
          .where("color")
          .is(color)
          .and("owner")
          .in(owner1, owner2)
          .and("status")
          .is(AssetStatus.ACTIVE.name())
          .sortBy("value", SortOrder.DESC)
          .limit(100)
          .execute();
    } catch (UncommittedStateException e) {
      throw new RuntimeException(e);
    }
  }

  @Transaction
  public List<Asset> queryAssetsByValueRange(HypernateContext ctx, int minValue, int maxValue) {
    if (minValue > maxValue) throw new IllegalArgumentException("minValue must be <= maxValue");
    try {
      return ctx.getRegistry()
          .query(Asset.class)
          .where("value")
          .between(minValue, maxValue)
          .and("status")
          .isNot(AssetStatus.ARCHIVED.name())
          .sortBy("value", SortOrder.ASC)
          .execute();
    } catch (UncommittedStateException e) {
      throw new RuntimeException(e);
    }
  }

  @Transaction
  public List<Asset> listAssetsByOwner(HypernateContext ctx, String owner) {
    return ctx.getRegistry().rangeQuery(Asset.class).withKeyPrefix(owner).execute();
  }

  @Transaction
  public List<Asset> listAllAssets(HypernateContext ctx) {
    return ctx.getRegistry().rangeQuery(Asset.class).fullScan().execute();
  }

  @Transaction
  public Asset initiateTransfer(
      HypernateContext ctx, String assetId, String currentOwner, String newOwner, int agreedPrice) {
    Registry registry = ctx.getRegistry();
    Asset asset = registry.mustRead(Asset.class, currentOwner, assetId);

    if (asset.getStatus() != AssetStatus.ACTIVE) {
      throw new RuntimeException(
          String.format(
              "Asset %s is not in ACTIVE status; current status: %s. Only ACTIVE assets can be transferred.",
              assetId, asset.getStatus()));
    }
    if (!asset.getOwner().equals(currentOwner)) {
      throw new RuntimeException(
          String.format(
              "Owner mismatch: asset %s is owned by %s, not %s",
              assetId, asset.getOwner(), currentOwner));
    }

    PricingResponse pricing =
        ctx.invoke("PricingChaincode", "validatePrice")
            .withArgs(new PricingRequest(assetId, asset.getValue(), newOwner, agreedPrice))
            .returning(PricingResponse.class)
            .execute();

    if (!pricing.valid()) {
      throw new RuntimeException("Transfer rejected by PricingChaincode: " + pricing.notes());
    }

    asset.setStatus(AssetStatus.PENDING_TRANSFER);
    registry.mustUpdate(asset);
    return asset;
  }

  @Transaction
  public Asset completeTransfer(
      HypernateContext ctx, String assetId, String currentOwner, String newOwner) {
    Registry registry = ctx.getRegistry();
    Asset asset = registry.mustRead(Asset.class, currentOwner, assetId);

    if (asset.getStatus() != AssetStatus.PENDING_TRANSFER) {
      throw new RuntimeException(
          String.format(
              "Asset %s is not pending transfer; status: %s", assetId, asset.getStatus()));
    }

    asset.setOwner(newOwner);
    asset.setStatus(AssetStatus.ACTIVE);
    asset.setTimestamp(ctx.getStub().getTxTimestamp().toEpochMilli());
    registry.mustUpdate(asset);
    return asset;
  }

  @Transaction
  public void archiveAsset(HypernateContext ctx, String owner, String id) {
    Asset asset = ctx.getRegistry().mustRead(Asset.class, owner, id);
    if (asset.getStatus() == AssetStatus.ARCHIVED) {
      throw new RuntimeException("Asset " + id + " is already archived");
    }
    asset.setStatus(AssetStatus.ARCHIVED);
    ctx.getRegistry().mustUpdate(asset);
  }

  @Transaction
  public void setSensitiveAssetPolicy(
      HypernateContext ctx, String id, String owner, String expression) {
    SensitiveAsset asset = ctx.getRegistry().mustRead(SensitiveAsset.class, owner, id);
    EndorsementPolicy policy = EndorsementPolicy.of(expression);
    ctx.getRegistry().setEndorsementPolicy(asset, policy);
  }
}

@Contract(
    name = "PricingChaincode",
    info =
        @Info(title = "PricingChaincode", description = "Mock pricing chaincode", version = "1.0"))
class PricingChaincode implements ContractInterface {

  @Transaction
  public String validatePrice(Context ctx, String pricingRequestJson) {
    PricingRequest req;
    try {
      req = JSON.deserialize(pricingRequestJson, PricingRequest.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON for PricingRequest: " + e.getMessage(), e);
    }

    if (req.currentValue() > 1000) {
      boolean approved = req.offeredPrice() >= req.currentValue() * 0.9;
      if (!approved) {
        try {
          return JSON.serialize(
              new PricingResponse(
                  false,
                  0,
                  String.format(
                      "Offered price %d is below 90%% of market value %d",
                      req.offeredPrice(), req.currentValue())));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    try {
      return JSON.serialize(
          new PricingResponse(
              true, req.offeredPrice(), "Transfer approved at price " + req.offeredPrice()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
