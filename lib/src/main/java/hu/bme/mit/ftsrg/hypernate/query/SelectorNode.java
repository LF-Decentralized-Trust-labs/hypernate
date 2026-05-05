/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.query;

sealed interface SelectorNode permits ComparisonNode, LogicalNode {}
