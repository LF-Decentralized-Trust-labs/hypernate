/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

/**
 * Thrown when the number of key parts supplied by a caller does not match the number of primary key
 * attributes declared for the entity.
 *
 * <p>This signals a mistake at the call site, unlike {@link MissingKeysException}, which signals
 * that the entity itself declares no usable primary key metadata.
 */
@StandardException
public class KeyArityException extends HypernateException {}
