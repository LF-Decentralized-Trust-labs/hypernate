/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.endorsement;

public class InvalidEndorsementPolicyException extends RuntimeException {
  public InvalidEndorsementPolicyException(String message) {
    super(message);
  }
}
