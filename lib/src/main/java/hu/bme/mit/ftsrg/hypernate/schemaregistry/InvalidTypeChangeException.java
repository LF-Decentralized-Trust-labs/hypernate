/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;

public class InvalidTypeChangeException extends HypernateException {
  public InvalidTypeChangeException(String fromTpye, String toType) {
    super("invalid data conversion form type: " + fromTpye + " to type: " + toType);
  }

  public InvalidTypeChangeException(String message) {
    super(message);
  }

  public InvalidTypeChangeException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidTypeChangeException(Throwable cause) {
    super(cause);
  }
}
