/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PrimaryKeyDescriptor {
  private final EntityMeta entityMeta;
  private final List<AttributeDescriptor> attributeDescriptors = new ArrayList<>();

  public void add(AttributeDescriptor desc) {
    attributeDescriptors.add(desc);
  }
}
