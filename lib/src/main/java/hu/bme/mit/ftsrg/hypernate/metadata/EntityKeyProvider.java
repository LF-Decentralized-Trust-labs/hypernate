/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

public interface EntityKeyProvider {

  String getKey(final Object entity);

  String getKeyForParts(final Object... keyPart);

  String getType();
}
