/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

@StandardException
public class InvalidTypeChangeException extends HypernateException {
  public InvalidTypeChangeException(String fromTpye, String toType) {
    super("invalid data conversion form type: " + fromTpye + " to type: " + toType);
  }
}
