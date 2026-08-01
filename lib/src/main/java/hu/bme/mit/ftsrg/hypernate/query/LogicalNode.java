/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import java.util.List;
import java.util.Objects;

record LogicalNode(String operator, List<SelectorNode> children) implements SelectorNode {

  LogicalNode {
    Objects.requireNonNull(operator, "operator must not be null");
    children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
  }
}
