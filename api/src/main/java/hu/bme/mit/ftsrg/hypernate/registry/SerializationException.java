/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

/**
 * Exception thrown when there was a problem during the serialization or deserialization of an
 * entity.
 */
@StandardException
public class SerializationException extends HypernateException {}
