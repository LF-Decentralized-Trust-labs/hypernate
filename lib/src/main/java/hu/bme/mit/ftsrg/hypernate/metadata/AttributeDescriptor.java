/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import java.lang.reflect.Field;
import lombok.NonNull;

/**
 * One attribute participating in an entity's primary key.
 *
 * <p>The mapper is held as a class rather than an instance: this graph describes <em>what kind</em>
 * of mapping an attribute uses, and stays free of live behaviour, so that it remains expressible in
 * a serialized form. Instantiating mappers is {@link EntityMetadataProvider}'s job.
 *
 * @param field the field on the entity class holding this attribute's value, already made readable
 * @param mapper the kind of mapper turning that value into a composite key segment
 */
record AttributeDescriptor(
    @NonNull Field field, @NonNull Class<? extends AttributeMapper> mapper) {}
