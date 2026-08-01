/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SelectorAssembler {

  private SelectorAssembler() {}

  static String assemble(
      final SelectorNode selector,
      final List<SortClause> sortClauses,
      final Integer limit,
      final Integer skip) {
    final Map<String, Object> queryDocument = new LinkedHashMap<>();
    queryDocument.put(
        "selector", selector == null ? new LinkedHashMap<>() : selectorToDocument(selector));

    if (!sortClauses.isEmpty()) {
      final List<Map<String, Object>> sort = new ArrayList<>();
      for (final SortClause clause : sortClauses) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(clause.field(), clause.order().jsonValue());
        sort.add(entry);
      }
      queryDocument.put("sort", sort);
    }

    if (limit != null) {
      queryDocument.put("limit", limit);
    }

    if (skip != null) {
      queryDocument.put("skip", skip);
    }

    return JSON.serialize(queryDocument);
  }

  private static Map<String, Object> selectorToDocument(final SelectorNode selector) {
    if (selector instanceof ComparisonNode comparison) {
      final Map<String, Object> operatorDocument = new LinkedHashMap<>();
      operatorDocument.put(comparison.operator(), comparison.value());

      final Map<String, Object> fieldDocument = new LinkedHashMap<>();
      fieldDocument.put(comparison.field(), operatorDocument);
      return fieldDocument;
    }

    final LogicalNode logicalNode = (LogicalNode) selector;
    if (CouchDbOperators.AND.equals(logicalNode.operator())) {
      return assembleAndNode(logicalNode);
    }

    final List<Map<String, Object>> children = new ArrayList<>();
    for (final SelectorNode child : logicalNode.children()) {
      children.add(selectorToDocument(child));
    }

    final Map<String, Object> logicalDocument = new LinkedHashMap<>();
    logicalDocument.put(logicalNode.operator(), children);
    return logicalDocument;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> assembleAndNode(final LogicalNode logicalNode) {
    final Map<String, Object> merged = new LinkedHashMap<>();
    for (final SelectorNode child : logicalNode.children()) {
      final Map<String, Object> childDocument = selectorToDocument(child);
      if (containsLogicalOperator(childDocument)) {
        final List<Map<String, Object>> children = new ArrayList<>();
        for (final SelectorNode logicalChild : logicalNode.children()) {
          children.add(selectorToDocument(logicalChild));
        }

        final Map<String, Object> andDocument = new LinkedHashMap<>();
        andDocument.put(CouchDbOperators.AND, children);
        return andDocument;
      }

      for (final Map.Entry<String, Object> entry : childDocument.entrySet()) {
        if (!merged.containsKey(entry.getKey())) {
          merged.put(entry.getKey(), entry.getValue());
          continue;
        }

        ((Map<String, Object>) merged.get(entry.getKey()))
            .putAll((Map<String, Object>) entry.getValue());
      }
    }

    return merged;
  }

  private static boolean containsLogicalOperator(final Map<String, Object> selectorDocument) {
    return selectorDocument.keySet().stream().anyMatch(key -> key.startsWith("$"));
  }

  record SortClause(String field, SortOrder order) {}
}
