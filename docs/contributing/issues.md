# Reporting Issues

Issues are the starting point for almost every contribution. A good issue helps maintainers
reproduce a problem or understand a request quickly.

## Before You Open an Issue

1. **Search existing issues** — your bug or idea may already be tracked. Adding to an existing
   discussion is more useful than opening a duplicate.
2. **Check the documentation** — the [Getting Started](../getting-started/index.md) and
   [Concepts](../concepts/index.md) sections may already answer your question.
3. **Ask on Discord** — if you are not sure whether something is a bug, ask first on
   [Discord](https://discord.gg/E3sMMC8mGc).

## Reporting a Bug

Open a [new issue](https://github.com/LF-Decentralized-Trust-labs/hypernate/issues/new/choose) and
choose the bug report template. Please include:

- **Hypernate version** — release tag or commit SHA.
- **Environment** — JDK version, Hyperledger Fabric version, and operating system.
- **What happened** versus **what you expected**.
- **Steps to reproduce** — a minimal code snippet or test is ideal.
- **Logs or stack traces**, if any.

The more precisely a maintainer can reproduce the problem, the faster it can be fixed.

## Requesting a Feature

Open a [new issue](https://github.com/LF-Decentralized-Trust-labs/hypernate/issues/new/choose) and
choose the feature request template. Please describe:

- **The problem** you are trying to solve, not just the solution you have in mind.
- **Your proposed approach**, if you have one.
- **Alternatives** you have considered.

## Issue Triage and the Design Workflow

Most of this lifecycle is enforced automatically:

1. **An issue is opened.** It is automatically labeled `needs-triage`.
2. **Maintainers triage and discuss the design.** While a design is being worked out, the issue
   carries `design/pending`.
3. **The design is approved.** A maintainer applies `design/approved`, and work can begin.
4. **A pull request links the approved issue** with a closing keyword (for example `Closes #123`).
   An automated check confirms the link before the PR can merge — see
   [the design-approval check](pull-requests.md#design-approval-check).

The labels used in this process:

| Label | Meaning |
| ----- | ------- |
| `needs-triage` | Added automatically to every new issue; awaiting maintainer review. |
| `design/pending` | The design is under active discussion — please wait before starting work. |
| `design/approved` | The design is approved; contributions are welcome. |
| `needs-approved-issue` | Added automatically to a pull request not linked to a `design/approved` issue. |
| `stale` | Added automatically to a flagged pull request after a period of inactivity. |

**Every contribution must be linked to an issue labeled `design/approved` before work begins —
including trivial fixes.** There is no exception for small changes: every change still counts
toward the project's health metrics (such as its error rate), so all changes go through the same
design-approval step. This is enforced by an automated check on every pull request — a PR not
linked to a `design/approved` issue is flagged and cannot merge. See
[the design-approval check](pull-requests.md#design-approval-check) for what that looks like and
how to resolve it.

## Reporting a Security Vulnerability

> **Do not report security vulnerabilities in public issues.** A private reporting process is
> being finalized. Until it is published here, please contact the [maintainers](maintainers.md)
> directly and avoid disclosing details in public channels.
