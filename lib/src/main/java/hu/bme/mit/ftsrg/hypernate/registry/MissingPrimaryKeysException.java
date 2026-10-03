/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

/**
 * Exception thrown when an asset has no {@link hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey
 * PrimaryKey} defined.
 */
@StandardException
public class MissingPrimaryKeysException extends HypernateException {}
