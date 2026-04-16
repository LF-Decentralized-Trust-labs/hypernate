# CI/CD

This project uses GitHub Actions for continuous integration and delivery.

## Workflows

| Workflow     | File                          | Purpose                                   |
| ------------ | ----------------------------- | ----------------------------------------- |
| Build & Test | `.github/workflows/build.yml` | Formatting checks, compilation, and tests |

## Build & Test

The Build & Test workflow runs `spotlessCheck`, `assemble`, and `test` on pushes and pull
requests that touch code-relevant files.

Documentation-only changes are skipped via path filters.

If a new commit is pushed while a run is already in progress for the same branch, the older run is
automatically canceled.

## Reading CI Results

1. **Status check on PRs** - The workflow result appears as a check on the pull request page.
2. **Actions tab** - Open the repository Actions tab to view workflow runs.
3. **Test report artifact** - Every run uploads an HTML test report artifact retained for 14 days.

## Troubleshooting

### Formatting Check Failed

The `spotlessCheck` step enforces Google Java Format. To fix locally:

```bash
./gradlew spotlessApply
```

This reformats source files in place. Commit the changes and push again.

### Test Failure

Check the uploaded test report artifact:

1. Open the failed workflow run in the Actions tab.
2. Scroll to the **Artifacts** section.
3. Download **test-report** and open `index.html` in a browser.

The report shows failing tests with stack traces and assertion messages.
