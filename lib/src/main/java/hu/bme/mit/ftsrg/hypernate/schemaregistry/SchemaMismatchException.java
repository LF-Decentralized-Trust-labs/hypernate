/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

@StandardException
public class SchemaMismatchException extends HypernateException {
  public SchemaMismatchException(String type) {
    super("Schema version mismatch on class: " + type);
  }
}
