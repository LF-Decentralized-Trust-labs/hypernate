/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.context;

import lombok.Getter;

@Getter
public class CrossChaincodeException extends RuntimeException {
  private final String chaincodeId;
  private final String functionName;
  private final int statusCode;
  private final String originalMessage;

  public CrossChaincodeException(String chaincodeId, String functionName, int statusCode, String originalMessage) {
    super(String.format("Cross-chaincode call to %s#%s failed with status %d: %s", 
        chaincodeId, functionName, statusCode, originalMessage));
    this.chaincodeId = chaincodeId;
    this.functionName = functionName;
    this.statusCode = statusCode;
    this.originalMessage = originalMessage;
  }
}
