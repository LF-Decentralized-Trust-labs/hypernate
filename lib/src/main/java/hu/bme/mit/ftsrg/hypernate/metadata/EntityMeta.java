/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class EntityMeta {
  private String className;
  @Setter private PrimaryKeyDescriptor primaryKeyDescriptor;
}
