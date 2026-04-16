/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

/** Base class for {@link HypernateException}s related to data access problems. */
@StandardException
public abstract class DataAccessException extends HypernateException {}
