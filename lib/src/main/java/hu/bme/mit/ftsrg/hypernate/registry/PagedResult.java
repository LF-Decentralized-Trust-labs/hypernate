/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.registry;

import java.util.List;

public class PagedResult<T> {
  private final List<T> results;
  private final String nextBookmark;
  private final boolean hasMore;

  public PagedResult(List<T> results, String nextBookmark, boolean hasMore) {
    this.results = results;
    this.nextBookmark = nextBookmark;
    this.hasMore = hasMore;
  }

  public List<T> results() {
    return results;
  }

  public String nextBookmark() {
    return nextBookmark;
  }

  public boolean hasMore() {
    return hasMore;
  }
}
