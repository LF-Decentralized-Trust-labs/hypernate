/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.sample;

public class PricingRequest {
  private String assetId;
  private int currentValue;
  private String newOwner;
  private int offeredPrice;

  public PricingRequest() {}

  public PricingRequest(String assetId, int currentValue, String newOwner, int offeredPrice) {
    this.assetId = assetId;
    this.currentValue = currentValue;
    this.newOwner = newOwner;
    this.offeredPrice = offeredPrice;
  }

  public String assetId() {
    return assetId;
  }

  public int currentValue() {
    return currentValue;
  }

  public String newOwner() {
    return newOwner;
  }

  public int offeredPrice() {
    return offeredPrice;
  }

  public String getAssetId() {
    return assetId;
  }

  public int getCurrentValue() {
    return currentValue;
  }

  public String getNewOwner() {
    return newOwner;
  }

  public int getOfferedPrice() {
    return offeredPrice;
  }
}
