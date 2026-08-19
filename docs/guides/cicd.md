# CI/CD

This project uses GitHub Actions for continuous integration and delivery.

## Workflows

| Workflow         | File                                         | Purpose                                                   |
| ---------------- | -------------------------------------------- | --------------------------------------------------------- |
| Build & Test     | `.github/workflows/build.yml`                | Formatting checks, compilation, and tests                 |
| Build Documentation | `.github/workflows/docs-build.yml`        | Lints Markdown and validates the documentation site       |
| Deploy Documentation | `.github/workflows/docs-deploy.yml`      | Publishes the documentation site via mike                 |
| Triage Label     | `.github/workflows/triage_label.yml`         | Labels newly opened issues with `needs-triage`            |
| Design Approval  | `.github/workflows/check_design_approval.yml`| Flags PRs without a linked design/approved issue          |
| Stale PRs        | `.github/workflows/stale_not_approved.yml`   | Marks and eventually closes stale, non-approved PRs       |
| Fast-Forward Merge | `.github/workflows/fast-forward.yml`       | Merges approved PRs by fast-forwarding `main`             |

## Build & Test

The Build & Test workflow runs `spotlessCheck`, `assemble`, and `test` on pushes and pull requests that touch code-relevant files.

Documentation-only changes are skipped via path filters.

If a new commit is pushed while a run is already in progress for the same branch, the older run is automatically canceled.

## Build Documentation

The Build Documentation workflow runs two checks, both identical to what contributors run locally:

1. `npx markdownlint-cli2` – the [Markdown source conventions](../contributing/documentation.md#markdown-conventions): one sentence per line, en-dashes, and 2-space list nesting.
2. `mkdocs build --strict` – a broken internal link or a page missing from `nav` fails the check instead of breaking the deployment after merge.

It runs on every pull request, regardless of which files changed.
This is deliberate: a required status check that is skipped by a path filter never reports its result, which would block the PR from merging.
Instead, a filter step inside the job detects which files changed and skips the lint and build steps when they have nothing to do, so unrelated PRs pass immediately.
On branch pushes (before a PR exists), path filters do apply, providing early feedback only when documentation files change.

Both this workflow and Deploy Documentation set up their Python environment through the shared composite action `.github/actions/setup-docs`.

## Deploy Documentation

The Deploy Documentation workflow publishes the site with [mike](https://github.com/jimporter/mike) on pushes to `main` (as the `latest` version) and on `v*.*.*` tags (as a versioned snapshot).
Deployments are serialized: concurrent runs would race on the `gh-pages` branch, so a new run queues behind an in-progress one instead of canceling it.

## Triage Label

When an issue is opened, the Triage Label workflow automatically adds the `needs-triage` label so new issues are easy to find and route.

## Design Approval

On every pull request (opened, edited, synchronized, or reopened), the Design Approval workflow checks that the PR links to an issue carrying the `design/approved` label.
PRs that lack such a linked issue are flagged via the `needs-approved-issue` label.

Because the workflow is triggered by changes to the PR, it will not rerun if a non-`design/approved` issue gets the `design/approved` label.
In these cases, either a maintainer can force rerun the check or you can make a dummy change to the PR body (like adding a newline) and it will trigger the workflow.

## Stale PRs

A scheduled daily job marks pull requests carrying the `needs-approved-issue` label as `stale` after 14 days of inactivity and closes them after 60 days.
PRs labeled `pinned` or `security` are exempt.
Issues are never affected.

## Fast-Forward Merge

When a maintainer comments `/fast-forward` on an approved pull request, this workflow advances `main` to the PR's head commit.
The commits land exactly as authored – hashes and GPG signatures preserved – keeping history linear and verifiable (see [Project Governance](../contributing/governance.md#merging-pull-requests) for the policy).
The workflow reacts only to a comment whose entire body is `/fast-forward`, written by a user with write access, on a non-draft pull request whose review decision is `APPROVED`.

## Reading CI Results

1. **Status check on PRs** - The workflow result appears as a check on the pull request page.
2. **Actions tab** - Open the repository Actions tab to view workflow runs.
3. **Test report artifact** - Every run uploads an HTML test report artifact retained for 14 days.

## Troubleshooting

### Formatting Check Failed

The `spotlessCheck` step enforces Google Java Format.
To fix locally:

```bash
./gradlew spotlessApply
```

This reformats source files in place.
Commit the changes and push again.

### Test Failure

Check the uploaded test report artifact:

1. Open the failed workflow run in the Actions tab.
2. Scroll to the **Artifacts** section.
3. Download **test-report** and open `index.html` in a browser.

The report shows failing tests with stack traces and assertion messages.
