/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import java.util.List;
import lombok.NonNull;

/**
 * The primary key of an entity.
 *
 * @param attributes the key's attributes, in the order they appear in the composite key
 */
record PrimaryKeyDescriptor(@NonNull List<AttributeDescriptor> attributes) {}
