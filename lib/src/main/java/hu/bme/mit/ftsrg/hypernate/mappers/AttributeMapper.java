/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

import java.util.function.Function;

/**
 * A function that maps an attribute to a String that will be persisted on the ledger.
 *
 * @see hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo
 */
public interface AttributeMapper extends Function<Object, String> {}
