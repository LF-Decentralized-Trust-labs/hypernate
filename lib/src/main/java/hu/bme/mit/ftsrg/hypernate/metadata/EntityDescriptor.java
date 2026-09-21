/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import lombok.NonNull;

/**
 * The Hypernate metadata for one entity class.
 *
 * @param clazz the entity class this metadata describes
 * @param primaryKey the entity's primary key
 */
record EntityDescriptor(@NonNull Class<?> clazz, @NonNull PrimaryKeyDescriptor primaryKey) {}
