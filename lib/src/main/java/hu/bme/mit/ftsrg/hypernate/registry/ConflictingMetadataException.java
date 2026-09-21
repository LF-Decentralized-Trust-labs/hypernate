/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

/**
 * Thrown when the classpath scan finds more than one source of primary key metadata for the same
 * entity, so that neither can be chosen without guessing.
 */
@StandardException
public class ConflictingMetadataException extends HypernateException {}
