/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry.query;

public class UncommittedStateException extends Exception {
  public UncommittedStateException(String message) {
    super(message);
  }
}
