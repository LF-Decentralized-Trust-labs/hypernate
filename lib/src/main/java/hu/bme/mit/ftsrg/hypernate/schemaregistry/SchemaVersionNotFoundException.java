/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

@StandardException
public class SchemaVersionNotFoundException extends HypernateException {

  public SchemaVersionNotFoundException(final String clazz) {
    super("SchemaVersion annotation missing on class: " + clazz);
  }
}
