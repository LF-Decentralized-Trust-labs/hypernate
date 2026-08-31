/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.context;

import lombok.Getter;

@Getter
public class CrossChaincodeDeserializationException extends RuntimeException {
  private final String chaincodeId;
  private final String functionName;
  private final Class<?> expectedType;

  public CrossChaincodeDeserializationException(
      String chaincodeId, String functionName, Class<?> expectedType, Throwable cause) {
    super(
        String.format(
            "Failed to deserialize response from %s#%s into %s: %s",
            chaincodeId, functionName, expectedType.getSimpleName(), cause.getMessage()),
        cause);
    this.chaincodeId = chaincodeId;
    this.functionName = functionName;
    this.expectedType = expectedType;
  }
}
