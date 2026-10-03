/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.endorsement;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EndorsementPolicyBuilder {
  private String combinator;
  private Integer threshold;
  private final List<String> clauses = new ArrayList<>();

  public EndorsementPolicyBuilder or() {
    this.combinator = "OR";
    return this;
  }

  public EndorsementPolicyBuilder and() {
    this.combinator = "AND";
    return this;
  }

  public EndorsementPolicyBuilder outOf(int n) {
    this.combinator = "OutOf";
    this.threshold = n;
    return this;
  }

  public OrgBuilder org(String orgName) {
    return new OrgBuilder(orgName);
  }

  public EndorsementPolicy build() {
    if (clauses.isEmpty()) {
      throw new IllegalStateException("No principals added");
    }
    if (combinator == null) {
      throw new IllegalStateException("Combinator (and/or/outOf) must be set");
    }

    String joinedClauses =
        clauses.stream().map(c -> "'" + c + "'").collect(Collectors.joining(", "));

    String expression;
    if ("OutOf".equals(combinator)) {
      if (threshold > clauses.size()) {
        throw new IllegalStateException(
            "OutOf threshold " + threshold + " exceeds number of principals " + clauses.size());
      }
      expression = String.format("OutOf(%d, %s)", threshold, joinedClauses);
    } else {
      expression = String.format("%s(%s)", combinator, joinedClauses);
    }

    return EndorsementPolicy.of(expression);
  }

  public class OrgBuilder {
    private final String orgName;

    private OrgBuilder(String orgName) {
      this.orgName = orgName;
    }

    public EndorsementPolicyBuilder peer() {
      clauses.add(orgName + "MSP.peer");
      return EndorsementPolicyBuilder.this;
    }

    public EndorsementPolicyBuilder member() {
      clauses.add(orgName + "MSP.member");
      return EndorsementPolicyBuilder.this;
    }

    public EndorsementPolicyBuilder admin() {
      clauses.add(orgName + "MSP.admin");
      return EndorsementPolicyBuilder.this;
    }

    public EndorsementPolicyBuilder client() {
      clauses.add(orgName + "MSP.client");
      return EndorsementPolicyBuilder.this;
    }
  }
}
