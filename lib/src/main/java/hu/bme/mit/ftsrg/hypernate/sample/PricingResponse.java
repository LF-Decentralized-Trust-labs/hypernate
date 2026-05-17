/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.sample;

public class PricingResponse {
  private boolean valid;
  private int approvedPrice;
  private String notes;

  public PricingResponse() {}

  public PricingResponse(boolean valid, int approvedPrice, String notes) {
    this.valid = valid;
    this.approvedPrice = approvedPrice;
    this.notes = notes;
  }

  public boolean valid() {
    return valid;
  }

  public int approvedPrice() {
    return approvedPrice;
  }

  public String notes() {
    return notes;
  }

  public boolean isValid() {
    return valid;
  }

  public int getApprovedPrice() {
    return approvedPrice;
  }

  public String getNotes() {
    return notes;
  }
}
