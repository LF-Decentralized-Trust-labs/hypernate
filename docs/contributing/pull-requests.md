# Submitting Pull Requests

When your change is ready, open a pull request (PR) against the `main` branch. This page describes
the conventions and checks your PR needs to pass.

## Before You Start

Every pull request — including trivial fixes — must be linked to an issue labeled `design/approved`
(see [Issue Triage and the Design Workflow](issues.md#issue-triage-and-the-design-workflow)). An
automated check enforces this: a PR with no associated `design/approved` issue will fail. Starting
from an approved design also avoids rework if the approach needs to change.

## Preparing Your Change

1. **Fork and branch.** Work on a branch with a meaningful name, for example
   `feat/entity-validation`, `fix/registry-npe`, or `docs/quickstart`.
2. **Keep it focused.** One logical change per PR. Smaller PRs are reviewed faster.
3. **Format and test.** Run `./gradlew build` locally so formatting, compilation, and tests all
   pass before you push (see [Working with the Code](development.md)).

## Commit Messages

Hypernate uses [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary). Each
commit message starts with a type and a short description:

```text
feat: add validation hook to the entity registry
fix: handle missing primary key in Registry.read
docs: document the middleware chain
```

Common types are `feat`, `fix`, `docs`, `refactor`, `test`, `build`, and `ci`.

## Sign-off (DCO)

Every commit must be signed off to certify the
[Developer Certificate of Origin](https://developercertificate.org/). Add a sign-off with the `-s`
flag:

```bash
git commit -s -m "fix: handle missing primary key in Registry.read"
```

This appends a `Signed-off-by:` line using your configured Git name and email, so make sure those
are set:

```bash
git config user.name "Your Name"
git config user.email "you@example.com"
```

A DCO check runs on every pull request. If it fails because a commit is not signed off, amend or
rebase and force-push:

```bash
# For the most recent commit only:
git commit --amend -s --no-edit

# For several commits on your branch:
git rebase --signoff main

git push --force-with-lease
```

## Opening the Pull Request

1. Push your branch to your fork.
2. Open a PR against `main`. The PR template includes a checklist — please fill it in.
3. **Link the approved issue** with a closing keyword — for example `Closes #123`. This is
   required and verified automatically (see [Design-Approval Check](#design-approval-check)).
4. Describe what changed and why, and call out anything reviewers should focus on.

## Continuous Integration

When you open a PR that touches code, the **Build & Test** workflow runs three steps in order:

1. `spotlessCheck` — formatting.
2. `assemble` — compilation.
3. `test` — the JUnit test suite.

All checks must pass before a PR can be merged. See the [CI/CD guide](../guides/cicd.md) for how to
read results and troubleshoot failures. Documentation-only changes skip this workflow.

## Design-Approval Check

A separate **Design Approval** check also runs on every pull request. It verifies that the PR links
an issue labeled `design/approved` using a **closing keyword** in the description — for example
`Closes #123` (a bare mention like `#123` does not count).

If no linked `design/approved` issue is found, the check:

- applies the `needs-approved-issue` label,
- posts a comment explaining how to fix it, and
- fails, blocking the merge.

To clear it, link an approved issue with a closing keyword (or get the linked issue approved by the
maintainers), then **push a commit or edit the PR description** to re-run the check — approving the
issue alone does not re-trigger it. Once it passes, the `needs-approved-issue` label is removed
automatically.

Pull requests left with `needs-approved-issue` are marked `stale` after 14 days of inactivity and
closed 60 days later (PRs labeled `pinned` or `security` are exempt). See the
[CI/CD guide](../guides/cicd.md) for the underlying workflows.

## Review

A maintainer will review your PR. Be ready to:

- Respond to feedback and push follow-up commits.
- Keep your branch up to date with `main` if asked.

Once it is approved and all checks are green, a maintainer will merge your contribution. Thank you!
