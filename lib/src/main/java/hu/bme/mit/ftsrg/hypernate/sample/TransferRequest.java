/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.sample;

public class TransferRequest {
  private String assetId;
  private String currentOwner;
  private String newOwner;
  private int agreedPrice;

  public TransferRequest() {}

  public TransferRequest(String assetId, String currentOwner, String newOwner, int agreedPrice) {
    this.assetId = assetId;
    this.currentOwner = currentOwner;
    this.newOwner = newOwner;
    this.agreedPrice = agreedPrice;
  }

  public String assetId() {
    return assetId;
  }

  public String currentOwner() {
    return currentOwner;
  }

  public String newOwner() {
    return newOwner;
  }

  public int agreedPrice() {
    return agreedPrice;
  }

  public String getAssetId() {
    return assetId;
  }

  public String getCurrentOwner() {
    return currentOwner;
  }

  public String getNewOwner() {
    return newOwner;
  }

  public int getAgreedPrice() {
    return agreedPrice;
  }
}
