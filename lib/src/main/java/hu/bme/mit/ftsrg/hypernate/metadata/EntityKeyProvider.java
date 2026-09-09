/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

@FunctionalInterface
public interface EntityKeyProvider {
  String getKey(Object entity);
}
