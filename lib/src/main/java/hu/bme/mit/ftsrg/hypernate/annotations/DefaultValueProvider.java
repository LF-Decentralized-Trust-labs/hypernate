/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

@FunctionalInterface
public interface DefaultValueProvider<T> {
  T get();
}
