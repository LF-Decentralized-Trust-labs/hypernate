# CI/CD

This project uses GitHub Actions for continuous integration and delivery.

## Workflows

| Workflow | File | Purpose |
|----------|------|---------|
| Build & Test | `.github/workflows/build.yml` | Formatting checks, compilation, and test suite |

## Build & Test

Runs `spotlessCheck`, `assemble`, and `test` on every push and pull request that touches code-relevant files. Documentation-only changes are skipped via path filters.

If a new commit is pushed while a run is already in progress for the same branch, the older run is automatically cancelled.

### Reading CI results

1. **Status check on PRs** — the workflow result appears as a check on the pull request page.
2. **Actions tab** — navigate to the repository's Actions tab to see all workflow runs.
3. **Test report artifact** — every run uploads an HTML test report. Download it from the Artifacts section of the workflow run summary (retained for 14 days).

### Troubleshooting

#### Formatting check failed

The `spotlessCheck` step enforces Google Java Format. To fix locally:

```bash
./gradlew spotlessApply
```

This reformats all source files in place. Commit the changes and push again.

#### Test failure

Check the uploaded test report artifact for details:

1. Go to the failed workflow run in the Actions tab.
2. Scroll to the **Artifacts** section.
3. Download **test-report** and open `index.html` in a browser.

The report shows which tests failed, with stack traces and assertion messages.
