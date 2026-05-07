/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.sample;

public class TransferResult {
  private boolean approved;
  private String reason;
  private int finalPrice;

  public TransferResult() {}

  public TransferResult(boolean approved, String reason, int finalPrice) {
    this.approved = approved;
    this.reason = reason;
    this.finalPrice = finalPrice;
  }

  public boolean approved() { return approved; }
  public String reason() { return reason; }
  public int finalPrice() { return finalPrice; }
  
  public boolean isApproved() { return approved; }
  public String getReason() { return reason; }
  public int getFinalPrice() { return finalPrice; }
}
