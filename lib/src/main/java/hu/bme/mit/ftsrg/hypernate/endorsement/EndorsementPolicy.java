/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.endorsement;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

public class EndorsementPolicy {
  @Getter private final String expression;

  private EndorsementPolicy(String expression) {
    this.expression = expression;
  }

  public static EndorsementPolicy of(String expression) {
    validate(expression);
    return new EndorsementPolicy(expression);
  }

  public static EndorsementPolicyBuilder builder() {
    return new EndorsementPolicyBuilder();
  }

  /**
   * NOTE: A production implementation would compile this expression to a SignaturePolicyEnvelope
   * protobuf byte array using Fabric's msp/policydsl package. This prototype uses UTF-8-encoded
   * expression strings for simplicity, which is not compatible with stub.setStateValidationParameter()
   * in a real network.
   */
  public byte[] getPolicyBytes() {
    return expression.getBytes(StandardCharsets.UTF_8);
  }

  private static void validate(String expr) {
    if (expr == null || expr.isBlank()) {
      throw new InvalidEndorsementPolicyException("Expression cannot be blank");
    }

    Stack<Integer> parenStack = new Stack<>();
    char[] chars = expr.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == '(') parenStack.push(i);
      else if (chars[i] == ')') {
        if (parenStack.isEmpty()) {
          throw new InvalidEndorsementPolicyException("Unexpected closing parenthesis at position " + i);
        }
        parenStack.pop();
      }
    }
    if (!parenStack.isEmpty()) {
      throw new InvalidEndorsementPolicyException(
          "Unclosed parenthesis: " + parenStack.size() + " opening parenthesis(es) never closed");
    }

    Pattern funcPattern = Pattern.compile("([A-Za-z]+)\\(");
    Matcher funcMatcher = funcPattern.matcher(expr);
    while (funcMatcher.find()) {
      String func = funcMatcher.group(1);
      if (!func.equals("AND") && !func.equals("OR") && !func.equals("OutOf")) {
        throw new InvalidEndorsementPolicyException(
            "Unsupported policy function '" + func + "'. Only AND, OR, OutOf are allowed.");
      }
      if (func.equals("OutOf")) {
        int contentStart = funcMatcher.end();
        int commaIndex = expr.indexOf(',', contentStart);
        int closeParen = expr.indexOf(')', contentStart);
        int firstArgEnd = (commaIndex != -1 && commaIndex < closeParen) ? commaIndex : closeParen;
        String firstArg = expr.substring(contentStart, firstArgEnd).trim();
        if (!firstArg.matches("\\d+")) {
          throw new InvalidEndorsementPolicyException(
              "OutOf() requires an integer as its first argument, found '" + firstArg + "'");
        }
      }
    }

    Pattern mspPattern = Pattern.compile("'([^']+)'");
    Matcher mspMatcher = mspPattern.matcher(expr);
    List<String> foundMsps = new ArrayList<>();
    while (mspMatcher.find()) {
      String msp = mspMatcher.group(1);
      foundMsps.add(msp);
      if (!msp.matches("^[A-Za-z0-9]+MSP\\.(peer|member|admin|client)$")) {
        throw new InvalidEndorsementPolicyException(
            "Invalid MSP principal '" + msp + "'. Expected format: OrgNameMSP.(peer|member|admin|client)");
      }
    }

    if (foundMsps.isEmpty()) {
      throw new InvalidEndorsementPolicyException("Expression contains no valid principals");
    }

    // Validate OutOf threshold semantics
    if (expr.contains("OutOf(")) {
        Matcher outOfMatcher = Pattern.compile("OutOf\\(\\s*(\\d+)\\s*,").matcher(expr);
        if (outOfMatcher.find()) {
            int threshold = Integer.parseInt(outOfMatcher.group(1));
            // Find closing paren matching this OutOf(
            int openIdx = outOfMatcher.end() - 1; // position of comma
            int stack = 1;
            int closeIdx = -1;
            for(int i = outOfMatcher.start() + 6; i < expr.length(); i++) {
                if (expr.charAt(i) == '(') stack++;
                if (expr.charAt(i) == ')') {
                    stack--;
                    if (stack == 0) {
                        closeIdx = i;
                        break;
                    }
                }
            }
            if (closeIdx != -1) {
                String outOfBody = expr.substring(outOfMatcher.end(), closeIdx);
                Matcher innerMspMatcher = mspPattern.matcher(outOfBody);
                int count = 0;
                while (innerMspMatcher.find()) count++;
                if (threshold > count) {
                    throw new InvalidEndorsementPolicyException("OutOf threshold " + threshold + " exceeds principal count " + count);
                }
            }
        }
    }
  }
}
