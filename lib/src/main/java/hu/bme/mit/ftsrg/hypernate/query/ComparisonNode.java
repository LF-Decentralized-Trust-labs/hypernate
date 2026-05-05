/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import java.util.Objects;

record ComparisonNode(String field, String operator, Object value) implements SelectorNode {

  ComparisonNode {
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(operator, "operator must not be null");
  }
}
