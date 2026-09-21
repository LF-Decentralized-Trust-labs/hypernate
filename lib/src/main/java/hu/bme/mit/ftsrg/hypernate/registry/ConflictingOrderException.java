/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

/**
 * Thrown when several fields of a key class declare the same {@code @KeyOrder} value, which would
 * leave the order of the resulting composite key's segments undefined.
 */
@StandardException
public class ConflictingOrderException extends HypernateException {}
