/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import lombok.experimental.StandardException;

/** Exception thrown when the queried entity could not be found. */
@StandardException
public class EntityNotFoundException extends DataAccessException {

  /**
   * Create a new {@link EntityNotFoundException} for a specific key.
   *
   * @param key the key to add to the exception message
   */
  public EntityNotFoundException(final String key) {
    super("Entity with key '%s' could not be found".formatted(key));
  }
}
