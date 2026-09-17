/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.mappers;

import java.util.function.Function;

/**
 * Turns a primary key attribute's value into the string used as a composite key segment.
 *
 * <p>Implementations must have a public no-argument constructor: Hypernate instantiates them
 * reflectively from the mapper class named in the key metadata.
 *
 * <p>Implementations must also be <strong>stateless and thread-safe</strong>. One instance per
 * mapper kind is shared process-wide and invoked concurrently, because Fabric dispatches
 * transactions on several threads.
 *
 * <p>Since the mapping determines the ledger key, an implementation's output must be stable across
 * releases: changing one makes existing entities unreachable at their old keys.
 */
public interface AttributeMapper extends Function<Object, String> {}
