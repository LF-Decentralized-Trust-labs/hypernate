/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

@StandardException
public class EntityTypeNotFoundException extends HypernateException {

  public EntityTypeNotFoundException(final String clazz) {
    super("EntityType annotation missing on class: " + clazz);
  }
}
