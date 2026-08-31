/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SelectorBuilder {
  private final Map<String, Object> selector = new HashMap<>();
  private final List<Map<String, Object>> orConditions = new ArrayList<>();

  @SuppressWarnings("unchecked")
  void addCondition(String field, String operator, Object value) {
    Object existing = selector.get(field);

    if (existing == null) {
      if (operator.equals("$eq")) {
        selector.put(field, value);
      } else {
        Map<String, Object> fieldConditions = new HashMap<>();
        fieldConditions.put(operator, value);
        selector.put(field, fieldConditions);
      }
    } else if (existing instanceof Map && !((Map<?, ?>) existing).containsKey(operator)) {
      ((Map<String, Object>) existing).put(operator, value);
    } else {
      List<Map<String, Object>> andList;
      if (selector.containsKey("$and")) {
        andList = (List<Map<String, Object>>) selector.get("$and");
      } else {
        andList = new ArrayList<>();
        selector.put("$and", andList);
      }

      Map<String, Object> newCond = new HashMap<>();
      if (operator.equals("$eq")) {
        newCond.put(field, value);
      } else {
        newCond.put(field, Map.of(operator, value));
      }
      andList.add(newCond);
    }
  }

  void addOr(List<Map<String, Object>> subSelectors) {
    if (subSelectors != null && !subSelectors.isEmpty()) {
      orConditions.addAll(subSelectors);
    }
  }

  Map<String, Object> build() {
    Map<String, Object> finalSelector = new HashMap<>(selector);
    if (!orConditions.isEmpty()) {
      if (finalSelector.containsKey("$or")) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existingOrs =
            (List<Map<String, Object>>) finalSelector.get("$or");
        existingOrs.addAll(orConditions);
      } else {
        finalSelector.put("$or", new ArrayList<>(orConditions));
      }
    }
    return finalSelector;
  }
}
