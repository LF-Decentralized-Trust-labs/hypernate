/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.serialization;

public interface EntitySerializer {

  <T> byte[] toBuffer(final T entity);

  <T> T fromBuffer(final byte[] buffer, final Class<T> clazz);
}
